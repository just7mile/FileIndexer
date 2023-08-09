package me.just7mile.fileindexer.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.just7mile.fileindexer.*
import me.just7mile.fileindexer.impl.watcher.FileSystemWatchServiceImpl
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Implementation of [FileIndexer] using inverted index.
 */
internal class InvertedIndexFileIndexer(builder: FileIndexerBuilder) : FileIndexer {
  /**
   * The tokenizer for parsing a file into words.
   */
  private val tokenizer: Tokenizer

  /**
   * The file system watcher for watching file and directory changes.
   */
  private var watchService: FileSystemWatchService

  init {
    tokenizer = builder.tokenizer ?: TokenizerImpl
    watchService = builder.watchService ?: FileSystemWatchServiceImpl
  }

  /**
   * Scope for indexer coroutines.
   */
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

  /**
   * A Map storing inverted index for the files.
   *
   * It is mapping the word to the file to the list of locations of the word in the file. Thus:
   * - The key is the word itself.
   * - The value is a map, where its key is the file path, and the value is the list of the word locations in the file.
   */
  private val indexes = ConcurrentHashMap<String, ConcurrentHashMap<String, List<WordLocation>>>()

  /**
   * List of files to index.
   */
  private val pathsToIndex = ConcurrentLinkedQueue<Path>()

  /**
   * Current state of the indexer.
   */
  private var currentState = FileIndexerState.CREATED

  /**
   * Mutex for controlling [currentState] access.
   */
  private val currentStateMutex = Mutex()

  /**
   * File change listener passed to [FileSystemWatchService.startWatching].
   */
  private val fileChangeListener = object : FileChangeListener {
    /**
     * When a new path created:
     * - If it is a directory then tries index its subtree, recursively.
     * - Otherwise, it parses and indexes the file.
     */
    override fun onPathCreated(path: Path) {
      if (path.isDirectory()) {
        scope.launch { addDirForIndexing(path) }
      } else if (path.isPlainTextFile()) {
        scope.launch { addFileIndexes(path.toFile()) }
      }
    }

    /**
     * When a path modified:
     * - If it is a directory then ignore it.
     * - Otherwise, update the [indexes] with the new file content.
     */
    override fun onPathModified(path: Path) {
      if (path.isPlainTextFile()) {
        scope.launch { updateFileIndexes(path.toFile()) }
      }
    }

    /**
     * Removes deleted path from indexing.
     */
    override fun onPathDeleted(path: Path) {
      scope.launch { removePathFromIndexing(path) }
    }
  }

  override suspend fun getCurrentState(): FileIndexerState = currentStateMutex.withLock { currentState }

  override suspend fun start(initialPathsToIndex: List<Path>?) {
    initialPathsToIndex?.forEach { validatePath(it) }

    currentStateMutex.withLock {
      if (currentState != FileIndexerState.CREATED) {
        throw IllegalStateException("The indexer is not in the initial state.")
      }

      initialPathsToIndex?.let { pathsToIndex.addAll(it) }
      scope.async {
        pathsToIndex.forEach {
          launch {
            addPathForIndexing(it)
          }
        }
      }.await()

      currentState = FileIndexerState.READY
    }
  }

  override suspend fun addPath(path: Path) {
    validatePath(path)

    val isIndexerReady = currentStateMutex.withLock {
      if (currentState == FileIndexerState.CANCELED) {
        throw IllegalStateException("Adding paths is not allowed after the indexer is canceled.")
      }

      pathsToIndex.add(path)
      currentState == FileIndexerState.READY
    }
    if (isIndexerReady) addPathForIndexing(path)
  }

  override suspend fun removePath(path: Path) {
    if (getCurrentState() == FileIndexerState.CANCELED) {
      throw IllegalStateException("Removing paths is not allowed after the indexer is canceled.")
    }

    removePathFromIndexing(path)
  }

  /**
   * Returns results sorted descending by the number of appearance in a file.
   * Thus, files that contain the [word] the most are located first.
   */
  override suspend fun searchWord(word: String): List<WordSearchResult> {
    currentStateMutex.withLock {
      if (currentState == FileIndexerState.CREATED) {
        throw IllegalStateException("Searching is not allowed til the indexer is ready.")
      }

      if (currentState == FileIndexerState.CANCELED) {
        throw IllegalStateException("Searching is not allowed after the indexer is canceled.")
      }
    }

    return indexes[word.lowercase()]
      ?.map { (filePath, locations) -> WordSearchResultImpl(File(filePath), locations) }
      ?.sortedByDescending { it.locations.size }
      ?: listOf()
  }

  override suspend fun cancel() {
    currentStateMutex.withLock {
      if (currentState == FileIndexerState.CREATED) {
        throw IllegalStateException("The indexer is has not been started yet.")
      }

      if (currentState == FileIndexerState.CANCELED) {
        throw IllegalStateException("The indexer is already canceled.")
      }

      watchService.clear()
      indexes.clear()
      scope.cancel()

      currentState = FileIndexerState.CANCELED
    }
  }

  /**
   * Adds [path] for indexing and starts a watcher for it.
   *
   *  @param path the path to add for indexing.
   */
  private suspend fun addPathForIndexing(path: Path) {
    if (path.isDirectory()) {
      addDirForIndexing(path)
    } else if (path.isPlainTextFile()) {
      addFileIndexes(path.toFile())
    }

    watchService.startWatching(path, fileChangeListener)
  }

  /**
   * Removes path from indexing - stops watcher and removes entries from [indexes].
   *
   *  @param path the path to remove from indexing.
   */
  private fun removePathFromIndexing(path: Path) {
    watchService.stopWatching(path)

    val absolutePath = path.toAbsolutePath()
    pathsToIndex.removeIf { it.toAbsolutePath().startsWith(absolutePath) }
    removeIndexes(path)
  }

  /**
   * Recursively adds a directory and its contents for indexing.
   *
   *  @param path the directory to add for indexing.
   */
  private suspend fun addDirForIndexing(path: Path) {
    coroutineScope {
      path.listDirectoryEntries().forEach {
        if (it.isDirectory()) {
          launch { addDirForIndexing(it) }
        } else if (it.isPlainTextFile()) {
          launch { addFileIndexes(it.toFile()) }
        }
      }
    }
  }

  /**
   * Resolves indexes of the provided file, and adds them to the [indexes].
   *
   * @param file the file to index.
   */
  private fun addFileIndexes(file: File) {
    val words = getFileWords(file)
    val absolutePath = file.absolutePath
    words.forEach { (word, locations) ->
      indexes.computeIfAbsent(word) { ConcurrentHashMap() }[absolutePath] = locations
    }
  }

  /**
   * Updates file indexes by adding/updating resolved indexes and removing the ones that do not exist anymore.
   * This is not very efficient, but has to be done this way, because it is not possible to get
   * the specific part of the changed file.
   * Another solution would be to store file indexes and use Myers algorithm to find the difference. But, this solution
   * is not memory efficient as it would require to store a copy of each file in the memory or in a storage.
   *
   * @param file the file to update its indexes.
   */
  private fun updateFileIndexes(file: File) {
    val words = getFileWords(file)
    val absolutePath = file.absolutePath
    words.forEach { (word, locations) ->
      indexes.computeIfAbsent(word) { ConcurrentHashMap() }[absolutePath] = locations
    }

    indexes.forEach { (word, filePathToWordLocations) ->
      if (words.containsKey(word)) return@forEach

      filePathToWordLocations.remove(absolutePath)
      if (filePathToWordLocations.isEmpty()) indexes.remove(word)
    }
  }

  /**
   * Removes path and its sub-paths indexes from the [indexes].
   * It is necessary to remove path and its sub-paths, because
   * if the path is already deleted, it is impossible to determine whether it was a folder or a regular file.
   *
   * @param path the path to remove its indexes.
   */
  private fun removeIndexes(path: Path) {
    val absolutePath = path.toAbsolutePath()
    indexes.forEach { (word, filePathToWordLocations) ->
      filePathToWordLocations.forEach { (filePath, _) ->
        if (Path.of(filePath).toAbsolutePath().startsWith(absolutePath)) filePathToWordLocations.remove(filePath)
      }
      if (filePathToWordLocations.isEmpty()) indexes.remove(word)
    }
  }

  /**
   * Parses the [file] content, converts all words to lowercase, groups similar words, and sorts the locations.
   *
   * @param file the to parse.
   * @return a map of each word with the list of its locations.
   */
  private fun getFileWords(file: File): Map<String, List<WordLocation>> {
    val result = mutableMapOf<String, MutableList<WordLocation>>()
    tokenizer.tokenize(file).forEach { token ->
      val lowercase = token.word.lowercase()
      result[lowercase] = (result[lowercase] ?: mutableListOf()).apply { add(WordLocationImpl(token.line, token.col)) }
    }

    return result.mapValues { (_, locations) ->
      locations.sortedWith { a, b -> if (a.line == b.line) a.col - b.col else a.line - b.line }
    }
  }

  /**
   * Checks if the path is eligible for indexing.
   *
   * @param path the path to validate.
   */
  private fun validatePath(path: Path) {
    require(path.exists()) { "Path not found: '${path.absolutePathString()}'." }
    require(path.isDirectory() || path.isPlainTextFile()) {
      "Indexing is not supported for the content of the file located at '${path.absolutePathString()}'."
    }
  }
}

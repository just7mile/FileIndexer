package me.just7mile.fileindexer.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.just7mile.fileindexer.*
import me.just7mile.fileindexer.impl.watcher.FileSystemWatchServiceImpl
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Implementation of [FileIndexer] using inverted index.
 */
internal class InvertedIndexFileIndexer(builder: FileIndexerBuilder) : FileIndexer {
  /**
   * The lexer for parsing a file into words.
   */
  private val wordLexer: WordLexer

  /**
   * The file system watcher for watching file and directory changes.
   */
  private var watchService: FileSystemWatchService

  init {
    wordLexer = builder.wordLexer ?: WordLexerImpl
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
  private val pathsToIndex = ConcurrentLinkedDeque<Path>()

  /**
   * Current state of the indexer.
   */
  private var currentState = FileIndexerState.CREATED

  /**
   * Mutex for controlling [currentState] access.
   */
  private val currentStateMutex = Mutex()

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

    if (getCurrentState() == FileIndexerState.CANCELED) {
      throw IllegalStateException("Adding paths is not allowed after the indexer is canceled.")
    }

    pathsToIndex.add(path)
    if (getCurrentState() == FileIndexerState.READY) addPathForIndexing(path)
  }

  override suspend fun removePath(path: Path) {
    if (getCurrentState() == FileIndexerState.CANCELED) {
      throw IllegalStateException("Removing paths is not allowed after the indexer is canceled.")
    }

    removePathFromIndexing(path)
  }

  /**
   * Returns results sorted descending by the number of appearance in a file.
   * Thus, files that contain the word [word] the most are located first.
   */
  override suspend fun searchWord(word: String): List<WordSearchResult> {
    if (getCurrentState() != FileIndexerState.READY) {
      throw IllegalStateException("Searching is not allowed til the indexer is ready.")
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
   * Checks if the path is eligible for indexing.
   *
   * @param path to validate.
   */
  private fun validatePath(path: Path) {
    require(path.exists()) { "Path not found: '${path.absolutePathString()}'." }
    require(path.isDirectory() || path.isPlainTextFile()) {
      "Indexing is not supported for the content of the file located at '${path.absolutePathString()}'."
    }
  }

  /**
   * Adds [path] for indexing and starts a watcher for it.
   *
   *  @param path to add for indexing.
   */
  private suspend fun addPathForIndexing(path: Path) {
    if (path.isDirectory()) {
      addDirForIndexing(path)
    } else if (path.isPlainTextFile()) {
      addFileIndexes(path.toFile())
    }

    val watcher = watchService.startWatching(path)
    watcher.receive() // wait for FileChangedEventType.INITIALIZED event.

    scope.launch { listenToWatcher(watcher) }
  }

  /**
   * Adds directory for indexing, recursively.
   *
   *  @param path to add for indexing.
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
   * Removes path from indexing.
   *
   *  @param path to add for indexing.
   */
  private fun removePathFromIndexing(path: Path) {
    watchService.stopWatching(path)

    val absolutePath = path.toAbsolutePath()
    pathsToIndex.removeIf { it.toAbsolutePath().startsWith(absolutePath) }
    removeIndexes(path)
  }

  /**
   * Listens to the file changes emitted by the file watcher, and reacts (re-indexes) to the file changes.
   *
   * @param watcher is the file watcher to listen to.
   */
  @OptIn(DelicateCoroutinesApi::class)
  private suspend fun listenToWatcher(watcher: Channel<FileChangedEvent>) {
    while (!watcher.isClosedForReceive) {
      try {
        val event = watcher.receive()
        when (event.type) {
          FileChangedEventType.INITIALIZED -> Unit
          FileChangedEventType.CREATED -> newPathReceived(event.path)
          FileChangedEventType.MODIFIED -> modifiedPathReceived(event.path)
          FileChangedEventType.DELETED -> deletedPathReceived(event.path)
        }
      } catch (_: ClosedReceiveChannelException) {
        break
      }
    }
  }

  /**
   * Invoked when a new path created inside a file watcher.
   * - If it is a directory then tries recursively index its subtree.
   * - Otherwise, it parses and indexes the file.
   *
   *  @param path created path.
   */
  private fun newPathReceived(path: Path) {
    if (path.isDirectory()) {
      scope.launch { addDirForIndexing(path) }
    } else if (path.isPlainTextFile()) {
      scope.launch { addFileIndexes(path.toFile()) }
    }
  }

  /**
   * Invoked when a path modified inside a file watcher.
   * - If it is a directory then ignore it.
   * - Otherwise, update the [indexes] with the new file content.
   *
   *  @param path modified path.
   */
  private fun modifiedPathReceived(path: Path) {
    if (path.isPlainTextFile()) {
      scope.launch { updateFileIndexes(path.toFile()) }
    }
  }

  /**
   * Invoked when a path deleted inside a file watcher.
   * - If it is a directory then removes its subtree from indexing.
   * - Otherwise, removes the file from the [indexes].
   *
   *  @param path deleted path.
   */
  private fun deletedPathReceived(path: Path) {
    scope.launch { removePathFromIndexing(path) }
  }

  /**
   * Resolves indexes of the provided file, and adds them to the [indexes].
   *
   * @param file to index.
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
   * is not memory efficient as it would require to store a copy of each file in the memory or in the storage.
   *
   * @param file to update its indexes.
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
   * Removes directory indexes recursively from the [indexes].
   *
   * @param path of the directory to remove its indexes.
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
   * Parses the [file] content, converts all words to lowercase and groups similar words and sorts the locations.
   *
   * @param file to parse.
   * @return map of each word with the list of locations.
   */
  private fun getFileWords(file: File): Map<String, List<WordLocation>> {
    val result = mutableMapOf<String, List<WordLocation>>()
    wordLexer.parse(file).forEach { (word, locations) ->
      if (locations.isNotEmpty()) {
        val lowercase = word.lowercase()
        result[lowercase] = (result[lowercase] ?: listOf()) + locations
      }
    }

    return result.mapValues { (_, locations) ->
      locations.sortedWith { a, b -> if (a.row == b.row) a.col - b.col else a.row - b.row }
    }
  }
}

package me.just7mile.fileindexer

import java.nio.file.Path

/**
 * A file indexer provides functionality for indexing and searching words in the files provided as the [start] function
 * parameter, or added by the [addPath] function.
 */
interface FileIndexer {
  /**
   * Sets the lexer for parsing a file into words.
   *
   * @param lexer the new word lexer.
   */
  fun setWordLexer(lexer: WordLexer)

  /**
   * Sets the watch service used for watching files and directory changes.
   *
   * @param service the new watch service.
   */
  fun setFileSystemWatchService(service: FileSystemWatchService)

  /**
   * Returns current state of the indexer.
   */
  fun getCurrentState(): FileIndexerState

  /**
   * Starts the indexing process.
   *
   * @param initialPathsToIndex is the list of paths to start with.
   */
  suspend fun start(initialPathsToIndex: List<Path>? = null)

  /**
   * Adds a new path to index.
   *
   * @param path to index.
   */
  suspend fun addPath(path: Path)

  /**
   * Removes a path from indexing.
   * The path should have been added during class initialization, or by the [addPath] function.
   *
   * @param path to remove.
   */
  suspend fun removePath(path: Path)

  /**
   * Searches a word in the indexed files.
   *
   * @param word to search (case-insensitive).
   * @return list of [WordSearchResult] which contains file and list of locations of the [word] in the file.
   */
  fun searchWord(word: String): List<WordSearchResult>

  /**
   * Cancels the indexer.
   */
  suspend fun cancel()
}
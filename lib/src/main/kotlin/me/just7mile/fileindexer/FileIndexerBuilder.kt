package me.just7mile.fileindexer

import me.just7mile.fileindexer.impl.InvertedIndexFileIndexer

/**
 * Builder for [FileIndexer].
 *
 * Implementation is not thread-safe.
 */
class FileIndexerBuilder {
  /**
   * The lexer for parsing a file into words.
   */
  internal var wordLexer: WordLexer? = null

  /**
   * The file system watcher for watching file and directory changes.
   */
  internal var watchService: FileSystemWatchService? = null

  /**
   * Sets the lexer for parsing a file into words.
   *
   * @param lexer the new word lexer.
   */
  fun setWordLexer(lexer: WordLexer) = apply {
    wordLexer = lexer
  }

  /**
   * Sets the watch service used for watching files and directory changes.
   *
   * @param service the new watch service.
   */
  fun setFileSystemWatchService(service: FileSystemWatchService) = apply {
    watchService = service
  }

  /**
   * Builds a new instance of [FileIndexer].
   */
  fun build(): FileIndexer = InvertedIndexFileIndexer(this)
}
package me.just7mile.fileindexer

import me.just7mile.fileindexer.impl.InvertedIndexFileIndexer

/**
 * Builder for [FileIndexer].
 *
 * Implementation is not thread-safe.
 */
class FileIndexerBuilder {
  /**
   * The tokenizer for parsing a file into words.
   */
  internal var tokenizer: Tokenizer? = null

  /**
   * The file system watcher for watching file and directory changes.
   */
  internal var watchService: FileSystemWatchService? = null

  /**
   * Sets the tokenizer for parsing a file into words.
   *
   * @param tokenizer the new word tokenizer.
   */
  fun setTokenizer(tokenizer: Tokenizer) = apply {
    this.tokenizer = tokenizer
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
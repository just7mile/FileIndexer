package me.just7mile.fileindexer

/**
 * Possible states of the [FileIndexer].
 */
enum class FileIndexerState {
  /**
   * The indexer's initial state,
   * meaning it is just created and has not done any processing.
   */
  CREATED,

  /**
   * When indexing files is done, and it is ready for accepting new files or search requests.
   */
  READY,

  /**
   * When the indexer is canceled. No more files or search requests are accepted.
   */
  CANCELED
}
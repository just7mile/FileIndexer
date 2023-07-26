package me.just7mile.fileindexer

/**
 * Type of events fired by the [FileSystemWatchService].
 */
enum class FileChangedEventType {
  /**
   * When the watcher service is initialized and watching for changes.
   */
  INITIALIZED,

  /**
   * When a new file or folder created in the watching directory.
   */
  CREATED,

  /**
   * When a file or folder modified in the watching directory.
   */
  MODIFIED,

  /**
   * When a file or folder deleted in the watching directory.
   */
  DELETED
}
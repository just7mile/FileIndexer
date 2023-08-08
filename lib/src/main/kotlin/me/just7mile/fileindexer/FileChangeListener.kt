package me.just7mile.fileindexer

import java.nio.file.Path

/**
 * A listener passed to [FileSystemWatchService.startWatching] to listens for file changes.
 */
interface FileChangeListener {
  /**
   * Invoked when a new path created.
   * @param path the created path.
   */
  fun onPathCreated(path: Path)

  /**
   * Invoked when a path modified.
   * @param path the modified path.
   */
  fun onPathModified(path: Path)

  /**
   * Invoked when a path deleted.
   * @param path the deleted path.
   */
  fun onPathDeleted(path: Path)
}
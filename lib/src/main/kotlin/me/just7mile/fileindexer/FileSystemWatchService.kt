package me.just7mile.fileindexer

import java.nio.file.Path

/**
 * A service that provides functionality for watching changes in the file system.
 */
interface FileSystemWatchService {

  /**
   * Starts watching the provided [path].
   *
   * @param path the path to watch.
   * @param listener the listener that listens to file change events.
   */
  fun startWatching(path: Path, listener: FileChangeListener)

  /**
   * Stops watching the provided [path].
   *
   * @param path the path to stop watching.
   */
  fun stopWatching(path: Path)

  /**
   * Stops all the active watchers.
   */
  fun clear()
}
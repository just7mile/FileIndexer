package me.just7mile.fileindexer.impl.watcher

import kotlinx.coroutines.channels.Channel
import me.just7mile.fileindexer.FileChangeListener
import java.nio.file.Path


/**
 * A base class for file watchers. A watcher implements [Channel],
 * as its main job is sending notification about file changes.
 *
 * It is extended by [DirWatcher] and [FileWatcher].
 *
 * @param path to watch. Can be either file or folder.
 * @param listener which listens to the change events.
 */
internal sealed class WatcherBase(protected val path: Path, protected val listener: FileChangeListener) {
  /**
   * Stops the watcher.
   */
  abstract fun stop()
}
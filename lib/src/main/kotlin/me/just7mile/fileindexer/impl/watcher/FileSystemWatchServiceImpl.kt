package me.just7mile.fileindexer.impl.watcher

import kotlinx.coroutines.channels.Channel
import me.just7mile.fileindexer.FileChangedEvent
import me.just7mile.fileindexer.FileSystemWatchService
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Implementation of [FileSystemWatchService].
 */
internal object FileSystemWatchServiceImpl : FileSystemWatchService {

  /**
   * A Map storing references to [WatcherBase] for each path.
   * It is used for clearing a path removed from watch by the [stopWatching] function.
   */
  private val watchers = ConcurrentHashMap<String, WatcherBase>()

  override fun startWatching(path: Path): Channel<FileChangedEvent> {
    require(path.exists()) { "Path does not exists." }

    val watcher = if (path.isDirectory()) DirWatcher(path) else FileWatcher(path)
    val absolutePath = path.absolutePathString()
    watchers[absolutePath] = watcher
    return watcher
  }

  override fun stopWatching(path: Path) {
    val absolutePath = path.absolutePathString()
    watchers.remove(absolutePath)?.cancel()
  }

  override fun clear() {
    watchers.apply {
      forEach { (_, watcher) -> watcher.cancel() }
      clear()
    }
  }
}
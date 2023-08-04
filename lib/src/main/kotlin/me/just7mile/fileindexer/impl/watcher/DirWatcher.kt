package me.just7mile.fileindexer.impl.watcher

import com.sun.nio.file.SensitivityWatchEventModifier
import kotlinx.coroutines.*
import me.just7mile.fileindexer.FileChangeListener
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.isDirectory

/**
 * Recursive directory watcher.
 */
internal class DirWatcher(path: Path, listener: FileChangeListener) : WatcherBase(path, listener) {
  init {
    require(path.isDirectory()) { "Provided path is not a directory." }
  }

  private val watchScope = CoroutineScope(Dispatchers.IO)
  private val watchService: WatchService = FileSystems.getDefault().newWatchService()

  private val registeredKeys = ConcurrentLinkedQueue<WatchKey>()
  private val treeChangeEventKinds = listOf(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)

  private val fileVisitor = object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
      dir?.let {
        registeredKeys += it.register(
          watchService,
          arrayOf(
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
          ),
          SensitivityWatchEventModifier.HIGH
        )
      }
      return FileVisitResult.CONTINUE
    }
  }

  init {
    registerKeys()
    watch()
  }

  private fun watch() = watchScope.launch {
    val job = coroutineContext.job.apply { invokeOnCompletion { stop() } }

    while (!job.isCancelled) {
      val watchingKey = runCatching { watchService.take() }.getOrElse {
        if (it is ClosedWatchServiceException || it is InterruptedException) null else throw it
      } ?: break

      val watchingDir = watchingKey.watchable() as? Path ?: break
      val events = watchingKey.pollEvents()
      val hasTreeChanged = events.any { event ->
        val eventPath = watchingDir.resolve(event.context() as Path)
        eventPath.isDirectory() && event.kind() in treeChangeEventKinds
      }
      watchingKey.reset()

      if (hasTreeChanged) registerKeys()

      events.forEach { event ->
        val eventPath = watchingDir.resolve(event.context() as Path)
        launch {
          when (event.kind()) {
            StandardWatchEventKinds.ENTRY_CREATE -> listener.onPathCreated(eventPath)
            StandardWatchEventKinds.ENTRY_DELETE -> listener.onPathDeleted(eventPath)
            else -> listener.onPathModified(eventPath)
          }
        }
      }
    }
  }

  override fun stop() {
    resetKeys()
    watchService.close()
    watchScope.cancel()
  }

  private fun registerKeys() {
    resetKeys()
    Files.walkFileTree(path, fileVisitor)
  }

  private fun resetKeys() {
    registeredKeys.apply {
      forEach { it.cancel() }
      clear()
    }
  }
}
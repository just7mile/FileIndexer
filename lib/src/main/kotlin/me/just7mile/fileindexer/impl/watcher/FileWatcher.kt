package me.just7mile.fileindexer.impl.watcher

import com.sun.nio.file.SensitivityWatchEventModifier
import kotlinx.coroutines.*
import me.just7mile.fileindexer.FileChangeListener
import java.nio.file.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile

/**
 * A single file watcher.
 */
internal class FileWatcher(path: Path, listener: FileChangeListener) : WatcherBase(path, listener) {
  init {
    require(path.isRegularFile()) { "Provided path is not a file." }
  }

  private val watchScope = CoroutineScope(Dispatchers.IO)
  private val watchService: WatchService = FileSystems.getDefault().newWatchService()

  // Parent folder is registered because [WatchService] works only with folders.
  private val registeredKey = path.parent.register(
    watchService,
    arrayOf(
      StandardWatchEventKinds.ENTRY_CREATE,
      StandardWatchEventKinds.ENTRY_DELETE,
      StandardWatchEventKinds.ENTRY_MODIFY
    ),
    SensitivityWatchEventModifier.HIGH
  )

  private val absolutePathString: String
    get() = path.absolutePathString()

  init {
    watch()
  }

  private fun watch() = watchScope.launch {
    val job = coroutineContext.job.apply { invokeOnCompletion { stop() } }

    while (!job.isCancelled) {
      val watchingKey = runCatching { watchService.take() }.getOrElse {
        if (it is ClosedWatchServiceException || it is InterruptedException) null else throw it
      } ?: break

      val watchingDir = watchingKey.watchable() as? Path ?: break
      watchingKey.pollEvents().forEach { event ->
        val eventPath = watchingDir.resolve(event.context() as Path)
        if (eventPath.absolutePathString() != absolutePathString) return@forEach

        launch {
          when (event.kind()) {
            StandardWatchEventKinds.ENTRY_CREATE -> listener.onPathCreated(eventPath)
            StandardWatchEventKinds.ENTRY_DELETE -> {
              listener.onPathDeleted(eventPath)
              stop()
            }

            else -> listener.onPathModified(eventPath)
          }
        }
      }

      watchingKey.reset()
    }
  }

  override fun stop() {
    registeredKey.cancel()
    watchService.close()
    watchScope.cancel()
  }
}

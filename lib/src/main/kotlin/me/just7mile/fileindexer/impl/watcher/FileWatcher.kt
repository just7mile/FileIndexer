package me.just7mile.fileindexer.impl.watcher

import com.sun.nio.file.SensitivityWatchEventModifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.just7mile.fileindexer.FileChangedEventType
import java.nio.file.*
import kotlin.io.path.absolutePathString

/**
 * A single file watcher.
 */
internal class FileWatcher(path: Path) : WatcherBase(path, Channel(Channel.CONFLATED)) {
  private val watchScope = CoroutineScope(Dispatchers.IO)
  private val watchService: WatchService = FileSystems.getDefault().newWatchService()

  private val registeredKey = path.parent
    .register(
      watchService, arrayOf(
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY
      ), SensitivityWatchEventModifier.HIGH
    )

  private val absolutePathString: String
    get() = path.absolutePathString()

  init {
    watch()
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun watch() = watchScope.launch {
    coroutineContext.job.invokeOnCompletion { close() }

    channel.send(FileChangedEventImpl(path, FileChangedEventType.INITIALIZED))

    while (!isClosedForSend) {
      val watchingKey = runCatching { watchService.take() }.getOrElse {
        if (it is ClosedWatchServiceException || it is InterruptedException) null else throw it
      } ?: break

      if (isClosedForSend) break

      val watchingDir = watchingKey.watchable() as? Path ?: break
      watchingKey.pollEvents().forEach { changeEvent ->
        val changedPath = watchingDir.resolve(changeEvent.context() as Path)
        if (changedPath.absolutePathString() != absolutePathString) return@forEach

        val eventType = when (changeEvent.kind()) {
          StandardWatchEventKinds.ENTRY_CREATE -> FileChangedEventType.CREATED
          StandardWatchEventKinds.ENTRY_DELETE -> FileChangedEventType.DELETED
          else -> FileChangedEventType.MODIFIED
        }
        channel.send(FileChangedEventImpl(changedPath, eventType))

        if (eventType == FileChangedEventType.DELETED) close()
      }

      watchingKey.reset()
    }
  }

  override fun close(cause: Throwable?): Boolean {
    registeredKey.cancel()
    watchService.close()
    watchScope.coroutineContext[Job]?.cancel()
    return channel.close(cause)
  }
}
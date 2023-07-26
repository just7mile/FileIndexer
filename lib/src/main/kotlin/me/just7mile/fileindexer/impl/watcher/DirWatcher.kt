package me.just7mile.fileindexer.impl.watcher

import com.sun.nio.file.SensitivityWatchEventModifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.just7mile.fileindexer.FileChangedEventType
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory

/**
 * Recursive directory watcher.
 */
internal class DirWatcher(path: Path) : WatcherBase(path, Channel()) {

  private val watchScope = CoroutineScope(Dispatchers.IO)
  private val watchService: WatchService = FileSystems.getDefault().newWatchService()

  private val registeredKeys = mutableListOf<WatchKey>()
  private val treeChangeEventType = listOf(FileChangedEventType.CREATED, FileChangedEventType.DELETED)

  init {
    registerKeys()
    watch()
  }

  private fun registerKeys() {
    resetKeys()

    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
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
    })
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
        val eventType = when (changeEvent.kind()) {
          StandardWatchEventKinds.ENTRY_CREATE -> FileChangedEventType.CREATED
          StandardWatchEventKinds.ENTRY_DELETE -> FileChangedEventType.DELETED
          else -> FileChangedEventType.MODIFIED
        }
        channel.send(FileChangedEventImpl(changedPath, eventType))

        if (changedPath.isDirectory() && eventType in treeChangeEventType) registerKeys()
      }

      watchingKey.reset()
    }
  }

  private fun resetKeys() {
    registeredKeys.apply {
      forEach { it.cancel() }
      clear()
    }
  }

  override fun close(cause: Throwable?): Boolean {
    resetKeys()
    watchService.close()
    watchScope.coroutineContext[Job]?.cancel()
    return channel.close(cause)
  }
}
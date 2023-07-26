package me.just7mile.fileindexer.impl.watcher

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.just7mile.fileindexer.FileChangedEvent
import me.just7mile.fileindexer.FileChangedEventType
import me.just7mile.fileindexer.TestFolderProvider
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSystemWatchServiceImplTest : TestFolderProvider() {

  @Test
  fun checksIfProvidedPathExists() = withTestFolder { testFolder ->
    val file = testFolder.resolve("file.txt")

    assertThrows<IllegalArgumentException>("throws exception as file.txt does not exist")
    { FileSystemWatchServiceImpl.startWatching(file.toPath()) }
  }

  @Test
  fun watchesAFile() = withTestFolder { testFolder ->
    runBlocking {
      val file1 = testFolder.resolve("file-1.txt")
        .apply { assertTrue("creates file-1.txt") { createNewFile() } }

      val file2 = testFolder.resolve("file-2.txt")
        .apply { assertTrue("creates file-2.txt") { createNewFile() } }

      val watcher = FileSystemWatchServiceImpl.startWatching(file1.toPath())

      var event = receiveEvent(watcher, "receives file-1.txt initialized event")
      assertEquals(file1.absolutePath, event.path.absolutePathString(), "event path is file-1.txt")
      assertEquals(FileChangedEventType.INITIALIZED, event.type, "watcher for file-1.txt initialized")

      file1.writeText("Hello World!")

      event = receiveEvent(watcher, "receives file-1.txt updated event")
      assertEquals(file1.absolutePath, event.path.absolutePathString(), "event path is file-1.txt")
      assertEquals(FileChangedEventType.MODIFIED, event.type, "event type is MODIFIED")

      file2.writeText("Hello World!")

      runBlocking {
        assertThrows<TimeoutCancellationException>("throws timeout exception because file watcher is watching only file-1.txt")
        { withTimeout(2100) { watcher.receive() } }
      }

      assertTrue("deletes file-1.txt") { file1.delete() }

      event = receiveEvent(watcher, "receives file-1.txt deleted event")
      assertEquals(file1.absolutePath, event.path.absolutePathString(), "event path is file-1.txt")
      assertEquals(FileChangedEventType.DELETED, event.type, "event type is DELETED")

      assertTrue("deletes file-2.txt") { file2.delete() }

      FileSystemWatchServiceImpl.clear()
    }
  }

  @Test
  fun watchesAFolder() = withTestFolder { testFolder ->
    runBlocking {
      val watcher = FileSystemWatchServiceImpl.startWatching(testFolder.toPath())
      var event = receiveEvent(watcher, "receives test folder initialized event")
      assertEquals(testFolder.absolutePath, event.path.absolutePathString(), "event path is test folder")
      assertEquals(FileChangedEventType.INITIALIZED, event.type, "watcher for test folder initialized")

      val rootFile = testFolder.resolve("root-file.txt")
        .apply { assertTrue("creates root-file.txt") { createNewFile() } }

      event = receiveEvent(watcher, "receives root-file.txt created event")
      assertEquals(rootFile.absolutePath, event.path.absolutePathString(), "event path is root-file.txt")
      assertEquals(FileChangedEventType.CREATED, event.type, "event type is CREATED")

      val rootFolder = testFolder.resolve("root-folder")
        .apply { assertTrue("creates root-folder") { mkdir() } }

      event = receiveEvent(watcher, "receives root-folder created event")
      assertEquals(rootFolder.absolutePath, event.path.absolutePathString(), "event path is root-folder")
      assertEquals(FileChangedEventType.CREATED, event.type, "event type is CREATED")

      rootFile.writeText("Hello World!")

      event = receiveEvent(watcher, "receives root-file.txt modified event")
      assertEquals(rootFile.absolutePath, event.path.absolutePathString(), "event path is root-file.txt")
      assertEquals(FileChangedEventType.MODIFIED, event.type, "event type is MODIFIED")

      assertTrue("deletes root-file.txt") { rootFile.delete() }

      event = receiveEvent(watcher, "receives root-file.txt deleted event")
      assertEquals(rootFile.absolutePath, event.path.absolutePathString(), "event path is root-file.txt")
      assertEquals(FileChangedEventType.DELETED, event.type, "event type is DELETED")

      val subFile = rootFolder.resolve("sub-file.txt")
        .apply { assertTrue("creates sub-file.txt") { createNewFile() } }

      event = receiveEvent(watcher, "receives root-folder modified event")
      assertEquals(rootFolder.absolutePath, event.path.absolutePathString(), "event path is root-folder")
      assertEquals(FileChangedEventType.MODIFIED, event.type, "event type is MODIFIED")

      event = receiveEvent(watcher, "receives sub-file.txt created event")
      assertEquals(subFile.absolutePath, event.path.absolutePathString(), "event path is sub-file.txt")
      assertEquals(FileChangedEventType.CREATED, event.type, "event type is CREATED")

      assertTrue("deletes root-folder") { rootFolder.deleteRecursively() }

      event = receiveEvent(watcher, "receives root-folder deleted event")
      assertEquals(rootFolder.absolutePath, event.path.absolutePathString(), "event path is root-folder")
      assertEquals(FileChangedEventType.DELETED, event.type, "event type is DELETED")

      FileSystemWatchServiceImpl.clear()
    }
  }

  private suspend fun receiveEvent(watcher: Channel<FileChangedEvent>, message: String = "") =
    assertDoesNotThrow(message) { withTimeout(2200) { watcher.receive() } }
}
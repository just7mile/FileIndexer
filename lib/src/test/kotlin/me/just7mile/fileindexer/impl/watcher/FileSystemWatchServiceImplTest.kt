package me.just7mile.fileindexer.impl.watcher

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.just7mile.fileindexer.FileChangeListener
import me.just7mile.fileindexer.TestFolderProvider
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSystemWatchServiceImplTest : TestFolderProvider() {

  @Test
  fun checksIfProvidedPathExists() = withTestFolder { testFolder ->
    val file = testFolder.resolve("file.txt")

    assertThrows<IllegalArgumentException>("throws exception as file.txt does not exist") {
      FileSystemWatchServiceImpl.startWatching(file.toPath(), object : FileChangeListener {
        override fun onPathCreated(path: Path) {}
        override fun onPathModified(path: Path) {}
        override fun onPathDeleted(path: Path) {}
      })
    }
  }

  @Test
  fun watchesAFile() = withTestFolder { testFolder ->
    runBlocking {
      val file1 = testFolder.resolve("file-1.txt")
        .apply { assertTrue("creates file-1.txt") { createNewFile() } }

      val file2 = testFolder.resolve("file-2.txt")
        .apply { assertTrue("creates file-2.txt") { createNewFile() } }

      val createdEvents = AtomicInteger()
      val modifiedEvents = AtomicInteger()
      val deletedEvents = AtomicInteger()

      FileSystemWatchServiceImpl.startWatching(file1.toPath(), object : FileChangeListener {
        override fun onPathCreated(path: Path) {
          createdEvents.incrementAndGet()
        }

        override fun onPathModified(path: Path) {
          modifiedEvents.incrementAndGet()
        }

        override fun onPathDeleted(path: Path) {
          deletedEvents.incrementAndGet()
        }
      })

      // 1 MODIFIED event
      file1.writeText("Hello World!")
      waitForWatchService()

      // Ignored, because it is watching only file-1.txt
      file2.writeText("Hello World!")
      waitForWatchService()

      // 1 DELETED event
      assertTrue("deletes file-1.txt") { file1.delete() }
      waitForWatchService()

      // Ignored, because it is watching only file-1.txt
      assertTrue("deletes file-2.txt") { file2.delete() }
      waitForWatchService()

      assertEquals(0, createdEvents.get(), "No CREATED events received")
      assertEquals(1, modifiedEvents.get(), "1 MODIFIED event received")
      assertEquals(1, deletedEvents.get(), "1 DELETED event received")

      FileSystemWatchServiceImpl.clear()
    }
  }

  @Test
  fun watchesAFolder() = withTestFolder { testFolder ->
    runBlocking {
      val createdEvents = AtomicInteger()
      val modifiedEvents = AtomicInteger()
      val deletedEvents = AtomicInteger()

      FileSystemWatchServiceImpl.startWatching(testFolder.toPath(), object : FileChangeListener {
        override fun onPathCreated(path: Path) {
          createdEvents.incrementAndGet()
        }

        override fun onPathModified(path: Path) {
          modifiedEvents.incrementAndGet()
        }

        override fun onPathDeleted(path: Path) {
          deletedEvents.incrementAndGet()
        }
      })

      // 1 CREATED event
      val rootFile = testFolder.resolve("root-file.txt")
        .apply { assertTrue("creates root-file.txt") { createNewFile() } }
      waitForWatchService()

      // 1 CREATED event
      val rootFolder = testFolder.resolve("root-folder")
        .apply { assertTrue("creates root-folder") { mkdir() } }
      waitForWatchService()

      // 1 MODIFIED event
      rootFile.writeText("Hello World!")
      waitForWatchService()

      // 1 DELETED event
      assertTrue("deletes root-file.txt") { rootFile.delete() }
      waitForWatchService()

      // 1 MODIFIED event (root-folder is modified), 1 CREATED event
      val subFile = rootFolder.resolve("sub-file.txt")
        .apply { assertTrue("creates sub-file.txt") { createNewFile() } }
      waitForWatchService()

      // 1 MODIFIED event
      subFile.writeText("Hello World!")
      waitForWatchService()

      // 1 DELETED event
      assertTrue("deletes root-folder") { rootFolder.deleteRecursively() }
      waitForWatchService()

      assertEquals(3, createdEvents.get(), "3 CREATED events received")
      assertEquals(3, modifiedEvents.get(), "3 MODIFIED events received")
      assertEquals(2, deletedEvents.get(), "2 DELETED events received")

      FileSystemWatchServiceImpl.clear()
    }
  }
  
  // Necessary to wait for WatchService to pick the changes (it does every 2 seconds)
  private fun waitForWatchService() = runBlocking { delay(2200) }
}
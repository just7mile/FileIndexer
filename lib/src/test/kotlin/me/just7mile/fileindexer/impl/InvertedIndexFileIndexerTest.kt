package me.just7mile.fileindexer.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import me.just7mile.fileindexer.FileIndexerBuilder
import me.just7mile.fileindexer.FileIndexerState
import me.just7mile.fileindexer.TestFolderProvider
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvertedIndexFileIndexerTest : TestFolderProvider() {

  @Test
  fun startFunctionChecksIfProvidedPathExists() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      val file = testFolder.resolve("file.txt")

      assertThrows<IllegalArgumentException> { fileIndexer.start(listOf(file.toPath())) }
    }
  }

  @Test
  fun startFunctionChecksIfProvidedPathIsPlainText() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      val file = testFolder.resolve("file.mp3")

      assertThrows<IllegalArgumentException> { fileIndexer.start(listOf(file.toPath())) }
    }
  }

  @Test
  fun startFunctionFailsIfIndexerWasCanceled() = runTest {
    val fileIndexer = FileIndexerBuilder().build()

    fileIndexer.start()
    fileIndexer.cancel()

    assertThrows<IllegalStateException> { fileIndexer.start() }
  }

  @Test
  fun addPathFunctionCanBeInvokedBeforeStart() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()

      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }

      assertDoesNotThrow { fileIndexer.addPath(file.toPath()) }

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun addPathFunctionFailsIfIndexerWasCanceled() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()

      fileIndexer.start()
      fileIndexer.cancel()

      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }

      assertThrows<IllegalStateException> { fileIndexer.addPath(file.toPath()) }

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun removePathFunctionCanBeInvokedBeforeStart() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      assertDoesNotThrow { fileIndexer.removePath(testFolder.resolve("file.txt").toPath()) }
    }
  }

  @Test
  fun removePathFunctionFailsIfIndexerWasCanceled() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()

      fileIndexer.start()
      fileIndexer.cancel()

      assertThrows<IllegalStateException> { fileIndexer.removePath(testFolder.resolve("file.txt").toPath()) }
    }
  }

  @Test
  fun searchWordFunctionFailsIfIndexerIsNotReady() = runTest {
    val fileIndexer = FileIndexerBuilder().build()

    assertThrows<IllegalStateException> { fileIndexer.searchWord("Hello") }
  }

  @Test
  fun cancelFunctionFailsIfIndexerWasNotStartedFirst() = runTest {
    val fileIndexer = FileIndexerBuilder().build()
    assertThrows<IllegalStateException> { fileIndexer.cancel() }
  }

  @Test
  fun cancelFunctionFailsIfIndexerWasAlreadyCanceled() = runTest {
    val fileIndexer = FileIndexerBuilder().build()
    fileIndexer.start()
    fileIndexer.cancel()
    assertThrows<IllegalStateException> { fileIndexer.cancel() }
  }

  @Test
  fun ignoresNotTextFiles() = withTestFolder { testFolder ->
    runTest {
      val file = testFolder.resolve("file.mp3")
        .apply { assertTrue("creates file.mp3") { createNewFile() } }

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(testFolder.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      val searchResult = fileIndexer.searchWord("test")
      assertEquals(0, searchResult.size, "search found in 0 result")

      fileIndexer.cancel()

      assertTrue("deletes file.mp3") { file.delete() }
    }
  }

  @Test
  fun indexesSingleFilePassedOnStart() = withTestFolder { testFolder ->
    runTest {
      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(file.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      val searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found in 1 result")

      val firstResult = searchResult.first()
      assertEquals(file.absolutePath, firstResult.file.absolutePath, "first result's file is file.txt")
      assertEquals(1, firstResult.locations.size, "word is found in only 1 location in the file.txt")

      val firstLocation = firstResult.locations.first()
      assertEquals(1, firstLocation.line, "location line in file.txt is 1")
      assertEquals(7, firstLocation.col, "location col in file.txt is 7")

      fileIndexer.cancel()

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun indexesDirectoryPassedOnStart() = withTestFolder { testFolder ->
    runTest {
      val file1 = testFolder.resolve("file-1.txt")
        .apply { assertTrue("creates file-1.txt") { createNewFile() } }
      file1.writeText("Hello World")

      val file2 = testFolder.resolve("file-2.txt")
        .apply { assertTrue("creates file-2.txt") { createNewFile() } }
      file2.writeText("World World")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(testFolder.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      val searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(2, searchResult.size, "search found 2 results")

      val file2Result = searchResult.first()
      val file1Result = searchResult[1]

      assertEquals(1, file1Result.locations.size, "1 locations in file-1.txt")

      val file1Location = file1Result.locations.first()
      assertEquals(1, file1Location.line, "location line in file-1.txt is 1")
      assertEquals(7, file1Location.col, "location col in file-1.txt is 7")

      assertEquals(2, file2Result.locations.size, "2 locations in file-2.txt")

      val file2FirstLocation = file2Result.locations.first()
      assertEquals(1, file2FirstLocation.line, "first location line in file-2.txt is 1")
      assertEquals(1, file2FirstLocation.col, "first location col in file-2.txt is 1")

      val file2SecondLocation = file2Result.locations[1]
      assertEquals(1, file2FirstLocation.line, "first location line in file-2.txt is 1")
      assertEquals(7, file2SecondLocation.col, "first location col in file-2.txt is 2")

      fileIndexer.cancel()

      assertTrue("deletes file-1.txt") { file1.delete() }
      assertTrue("deletes file-2.txt") { file2.delete() }
    }
  }

  @Test
  fun indexesPathAddedAfterStart() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start()
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(0, searchResult.size, "search did not find any results")

      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World")

      fileIndexer.addPath(file.toPath())

      searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found 1 result")

      fileIndexer.cancel()

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun removesIndexesOfPathRemovedAfterStart() = withTestFolder { testFolder ->
    runTest {
      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(file.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found 1 result")

      fileIndexer.removePath(file.toPath())

      searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(0, searchResult.size, "search did not find any results")

      fileIndexer.cancel()

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun indexesCreatedPathAfterStart() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(testFolder.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(0, searchResult.size, "search did not find any results")

      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World")

      waitForWatchService()

      searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found 1 result")

      fileIndexer.cancel()

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun removesIndexesOfDeletedPathAfterStart() = withTestFolder { testFolder ->
    runTest {
      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(file.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found 1 result")

      assertTrue("deletes file.txt") { file.delete() }

      waitForWatchService()

      searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(0, searchResult.size, "search did not find any results")

      fileIndexer.cancel()
    }
  }

  @Test
  fun updatesIndexesOfChangedFile() = withTestFolder { testFolder ->
    runTest {
      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World\n")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(testFolder.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found 1 result")
      assertEquals(1, searchResult.first().locations.size, "1 location in file.txt")

      file.appendText("Hello World")

      waitForWatchService()

      searchResult = fileIndexer.searchWord("heLLo")
      assertEquals(1, searchResult.size, "search found 1 result")
      assertEquals(2, searchResult.first().locations.size, "2 locations in file.txt")

      fileIndexer.cancel()

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun updatesIndexesOfChangedNestedFiles() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(testFolder.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("test")
      assertEquals(0, searchResult.size, "search did not find any results")

      val rootFolder = testFolder.resolve("root-folder")
        .apply { assertTrue("creates root-folder") { mkdir() } }

      val file = rootFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt inside root-folder") { createNewFile() } }
      file.writeText("test test test")

      waitForWatchService()

      searchResult = fileIndexer.searchWord("test")
      assertEquals(1, searchResult.size, "search found 1 result")
      assertEquals(3, searchResult.first().locations.size, "3 locations in file.txt")

      assertTrue("deletes root-folder") { rootFolder.deleteRecursively() }

      waitForWatchService()

      searchResult = fileIndexer.searchWord("test")
      assertEquals(0, searchResult.size, "search did not find any results")

      fileIndexer.cancel()
    }
  }

  @Test
  fun canAddPathsConcurrently() = withTestFolder { testFolder ->
    runTest {
      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start()
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      val numberOfCoroutines = 50

      coroutineScope {
        repeat(numberOfCoroutines) { index ->
          launch(Dispatchers.IO) {
            val file = testFolder.resolve("file-$index.txt").apply {
              assertTrue("creates file-$index.txt") { createNewFile() }
              writeText("test test test")
            }
            fileIndexer.addPath(file.toPath())
          }
        }
      }

      val searchResult = fileIndexer.searchWord("test")
      assertEquals(numberOfCoroutines, searchResult.size, "search found $numberOfCoroutines results")
      assertEquals(
        numberOfCoroutines * 3,
        searchResult.sumOf { it.locations.size },
        "search found ${numberOfCoroutines * 3} locations"
      )

      fileIndexer.cancel()
    }
  }

  @Test
  fun indexesConcurrentlyCreatedAndDeletedFiles() = withTestFolder { testFolder ->
    runTest {
      val folder1 = testFolder.resolve("folder-1")
        .apply { assertTrue("creates folder-1") { mkdir() } }

      val folder2 = testFolder.resolve("folder-2")
        .apply { assertTrue("creates folder-2") { mkdir() } }

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(folder1.toPath(), folder2.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      val numberOfCoroutines = 50

      coroutineScope {
        repeat(numberOfCoroutines) { index ->
          launch(Dispatchers.IO) {
            (if (index % 2 == 0) folder1 else folder2).resolve("file-$index.txt").apply {
              assertTrue("creates file-$index.txt") { createNewFile() }
              writeText("test test test")
            }
          }
        }
      }

      waitForWatchService()

      var searchResult = fileIndexer.searchWord("test")
      assertEquals(numberOfCoroutines, searchResult.size, "search found $numberOfCoroutines results")
      assertEquals(
        numberOfCoroutines * 3,
        searchResult.sumOf { it.locations.size },
        "search found ${numberOfCoroutines * 3} locations"
      )

      coroutineScope {
        repeat(numberOfCoroutines) { index ->
          launch(Dispatchers.IO) {
            (if (index % 2 == 0) folder1 else folder2).resolve("file-$index.txt")
              .apply { assertTrue("deletes file-$index.txt") { delete() } }
          }
        }
      }

      waitForWatchService()

      searchResult = fileIndexer.searchWord("test")
      assertTrue("search result is empty") { searchResult.isEmpty() }

      fileIndexer.cancel()
    }
  }

  @Test
  fun addPathWaitsForReadyState() = withTestFolder { testFolder ->
    runTest {
      val file = testFolder.resolve("file.txt")
        .apply { assertTrue("creates file.txt") { createNewFile() } }
      file.writeText("Hello World\n")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      val addPathDeferred = async { fileIndexer.addPath(file.toPath()) }

      assertThrows<IllegalStateException>("throws state exception because indexer is not ready")
      { fileIndexer.searchWord("hello") }

      fileIndexer.start()
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      addPathDeferred.await()

      val searchResult = fileIndexer.searchWord("wORlD")
      assertEquals(1, searchResult.size, "search found 1 result")
      assertEquals(1, searchResult.first().locations.size, "1 location in file.txt")

      fileIndexer.cancel()

      assertTrue("deletes file.txt") { file.delete() }
    }
  }

  @Test
  fun fileNamePrefixedWithAnotherFileNameWontBeDeleted() = withTestFolder { testFolder ->
    runTest {
      val file1 = testFolder.resolve("file-1.txt")
        .apply { assertTrue("creates file-1.txt") { createNewFile() } }
      file1.writeText("Hello World\n")
      val file11 = testFolder.resolve("file-1.txt-1.txt")
        .apply { assertTrue("creates file-1.txt-1.txt") { createNewFile() } }
      file11.writeText("Hello World\n")

      val fileIndexer = FileIndexerBuilder().build()
      assertEquals(FileIndexerState.CREATED, fileIndexer.getCurrentState(), "Indexer is in initial state")

      fileIndexer.start(listOf(testFolder.toPath()))
      assertEquals(FileIndexerState.READY, fileIndexer.getCurrentState(), "Indexer is ready")

      var searchResult = fileIndexer.searchWord("hello")
      assertEquals(2, searchResult.size, "search found 2 result")

      assertTrue("deletes file-1.txt") { file1.delete() }
      waitForWatchService()

      searchResult = fileIndexer.searchWord("hello")
      assertEquals(1, searchResult.size, "search found 1 result")

      fileIndexer.cancel()

      assertTrue("deletes file-1.txt-1.txt") { file11.delete() }
    }
  }

  // Necessary to wait for WatchService to pick the changes (it does every 2 seconds)
  private fun waitForWatchService() = runBlocking { delay(2200) }
}
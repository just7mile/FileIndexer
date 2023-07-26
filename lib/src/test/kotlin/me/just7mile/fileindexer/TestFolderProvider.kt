package me.just7mile.fileindexer

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.io.File
import java.util.*
import kotlin.test.assertTrue

abstract class TestFolderProvider {

  companion object {
    @JvmStatic
    private lateinit var playgroundFolder: File

    @BeforeAll
    @JvmStatic
    fun createTestFolder() {
      playgroundFolder = File("src/test/resources/playground")
      if (playgroundFolder.exists()) deleteTestFolder()

      assertTrue("creates playground folder for testing") { playgroundFolder.mkdirs() }
    }

    @AfterAll
    @JvmStatic
    fun deleteTestFolder() {
      assertTrue("deletes playground folder for testing") { playgroundFolder.deleteRecursively() }
    }
  }

  protected fun withTestFolder(body: (File) -> Unit) {
    val testFolder = playgroundFolder.resolve(UUID.randomUUID().toString())
      .apply { assertTrue("creates test folder") { mkdir() } }

    body(testFolder)

    assertTrue("deletes test folder") { testFolder.deleteRecursively() }
  }
}
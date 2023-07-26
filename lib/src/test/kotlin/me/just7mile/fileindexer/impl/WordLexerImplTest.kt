package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.TestFolderProvider
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WordLexerImplTest : TestFolderProvider() {

  @Test
  fun checksIfFileExists() = withTestFolder { testFolder ->
    val file = testFolder.resolve("file.txt")

    assertThrows<IllegalArgumentException>("throws exception as file.txt does not exist")
    { WordLexerImpl.parse(file) }
  }

  @Test
  fun checksIfFileIsNotADirectory() = withTestFolder { testFolder ->
    val file = testFolder.resolve("folder")
      .apply { assertTrue("creates folder") { mkdir() } }

    assertThrows<IllegalArgumentException>("throws exception as folders are not supported")
    { WordLexerImpl.parse(file) }

    assertTrue("deletes folder") { file.delete() }
  }

  @Test
  fun ignoredWordsAreNotParsed() = withTestFolder { testFolder ->
    val file = testFolder.resolve("text-file.txt")
      .apply { assertTrue("creates text-file.txt") { createNewFile() } }

    file.writeText("is the am")
    val result = WordLexerImpl.parse(file)
    assertTrue { result.isEmpty() }

    assertTrue("deletes folder") { file.delete() }
  }

  @Test
  fun checksFileContentEligibility() = withTestFolder { testFolder ->
    val file = testFolder.resolve("music-file.mp3")
      .apply { assertTrue("creates music-file.mp3") { createNewFile() } }

    assertThrows<IllegalArgumentException>("throws exception as mp3 file is not supported")
    { WordLexerImpl.parse(file) }

    assertTrue("deletes music-file.mp3") { file.delete() }
  }

  @Test
  fun correctlyParsesTextFile() = withTestFolder { testFolder ->
    val file = testFolder.resolve("text-file.txt")
      .apply { assertTrue("creates text-file.txt") { createNewFile() } }

    file.writeText("Hello\n")
    file.appendText("  World")

    val result = WordLexerImpl.parse(file)

    assertEquals(2, result.size, "lexer returns 2 words")

    val helloLocations = assertNotNull(result["Hello"], "'Hello' is present")
    assertEquals(1, helloLocations.size, "'Hello' has only 1 location")
    assertEquals(1, helloLocations.first().row, "'Hello' row is 1")
    assertEquals(1, helloLocations.first().col, "'Hello' col is 1")

    val worldLocations = assertNotNull(result["World"], "'World' is present")
    assertEquals(1, worldLocations.size, "'World' has only 1 location")
    assertEquals(2, worldLocations.first().row, "'World' row is 2")
    assertEquals(3, worldLocations.first().col, "'World' col is 3")

    assertTrue("deletes text-file.txt") { file.delete() }
  }
}
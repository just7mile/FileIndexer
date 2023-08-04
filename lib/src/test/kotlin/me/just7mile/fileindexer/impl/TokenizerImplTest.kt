package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.TestFolderProvider
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerImplTest : TestFolderProvider() {

  @Test
  fun checksIfFileExists() = withTestFolder { testFolder ->
    val file = testFolder.resolve("file.txt")

    assertThrows<IllegalArgumentException>("throws exception as file.txt does not exist")
    { TokenizerImpl.tokenize(file) }
  }

  @Test
  fun checksIfFileIsNotADirectory() = withTestFolder { testFolder ->
    val file = testFolder.resolve("folder")
      .apply { assertTrue("creates folder") { mkdir() } }

    assertThrows<IllegalArgumentException>("throws exception as folders are not supported")
    { TokenizerImpl.tokenize(file) }

    assertTrue("deletes folder") { file.delete() }
  }

  @Test
  fun ignoredWordsAreNotParsed() = withTestFolder { testFolder ->
    val file = testFolder.resolve("text-file.txt")
      .apply { assertTrue("creates text-file.txt") { createNewFile() } }

    file.writeText("is the am")
    val result = TokenizerImpl.tokenize(file)
    assertTrue { result.isEmpty() }

    assertTrue("deletes folder") { file.delete() }
  }

  @Test
  fun checksFileContentEligibility() = withTestFolder { testFolder ->
    val file = testFolder.resolve("music-file.mp3")
      .apply { assertTrue("creates music-file.mp3") { createNewFile() } }

    assertThrows<IllegalArgumentException>("throws exception as mp3 file is not supported")
    { TokenizerImpl.tokenize(file) }

    assertTrue("deletes music-file.mp3") { file.delete() }
  }

  @Test
  fun correctlyParsesTextFile() = withTestFolder { testFolder ->
    val file = testFolder.resolve("text-file.txt")
      .apply { assertTrue("creates text-file.txt") { createNewFile() } }

    file.writeText("Hello\n")
    file.appendText("  World")

    val result = TokenizerImpl.tokenize(file)

    assertEquals(2, result.size, "tokenizer returns 2 words")

    val helloToken = result.first()
    assertEquals("Hello", helloToken.word, "'Hello' word is found")
    assertEquals(1, helloToken.line, "'Hello' line is 1")
    assertEquals(1, helloToken.col, "'Hello' column is 1")

    val worldToken = result[1]
    assertEquals("World", worldToken.word, "'World' word is found")
    assertEquals(2, worldToken.line, "'World' line is 2")
    assertEquals(3, worldToken.col, "'World' column is 3")

    assertTrue("deletes text-file.txt") { file.delete() }
  }
}
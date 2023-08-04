package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.Token
import me.just7mile.fileindexer.Tokenizer
import java.io.File
import java.nio.file.Files

/**
 * Implementation of [Tokenizer].
 */
internal object TokenizerImpl : Tokenizer {

  private val ignoredWords = listOf("is", "the", "am")

  /**
   * Parses the [file] into words that have at least 2 characters,
   * ignoring the ones that are provided in the [ignoredWords].
   */
  override fun tokenize(file: File): List<Token> {
    require(file.exists()) { "Provided file does not exist." }
    require(file.isFile) { "Provided file is not a regular file." }

    val contentType = Files.probeContentType(file.toPath())
    require(contentType == "text/plain") { "Unsupported content type: '$contentType'." }

    val result = mutableListOf<Token>()
    var lineNum = 0
    file.forEachLine { line ->
      lineNum++
      var colNum = 1
      var word = ""
      line.split("").forEach { token ->
        if (token.isEmpty()) return@forEach
        if (token.matches(Regex("\\w+"))) {
          word += token
        } else {
          if (word.length > 1 && !ignoredWords.contains(word)) {
            result.add(TokenImpl(word, lineNum, colNum - word.length))
          }
          word = ""
        }
        colNum += token.length
      }

      if (word.length > 1 && !ignoredWords.contains(word)) {
        result.add(TokenImpl(word, lineNum, colNum - word.length))
      }
    }

    return result
  }
}
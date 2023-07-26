package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.WordLexer
import me.just7mile.fileindexer.WordLocation
import java.io.File
import java.nio.file.Files

/**
 * Implementation of [WordLexer].
 */
object WordLexerImpl : WordLexer {

  private val ignoredWords = listOf("is", "the", "am")

  override fun parse(file: File): Map<String, List<WordLocation>> {
    require(file.exists()) { "Provided file does not exist." }
    require(file.isFile) { "Provided file is not a regular file." }

    val contentType = Files.probeContentType(file.toPath())
    require(contentType == "text/plain") { "Unsupported content type: '$contentType'." }

    val result = mutableMapOf<String, MutableList<WordLocation>>()
    var row = 0
    file.forEachLine { line ->
      row++
      var col = 1
      var word = ""
      line.split("").forEach { token ->
        if (token.isEmpty()) return@forEach
        if (token.matches(Regex("\\w+"))) {
          word += token
        } else {
          if (word.length > 1 && !ignoredWords.contains(word)) {
            result[word] = result[word] ?: mutableListOf()
            result[word]?.add(WordLocationImpl(row, col - word.length))
          }
          word = ""
        }
        col += token.length
      }

      if (word.length > 1 && !ignoredWords.contains(word)) {
        result[word] = result[word] ?: mutableListOf()
        result[word]?.add(WordLocationImpl(row, col - word.length))
      }
    }

    return result
  }
}
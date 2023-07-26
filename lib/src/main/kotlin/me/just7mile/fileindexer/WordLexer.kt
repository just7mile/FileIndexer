package me.just7mile.fileindexer

import java.io.File

/**
 * A lexer for parsing words from a file.
 * It is called lexer not tokenizer, because it provides additional context for each word (token),
 * such as its location in the file.
 */
interface WordLexer {

  /**
   * Parses provided file.
   *
   * @param file to parse.
   * @return returns a Map where keys are words and values are list of locations where the word is located in the file.
   */
  fun parse(file: File): Map<String, List<WordLocation>>
}
package me.just7mile.fileindexer

import java.io.File

/**
 * A tokenizer for parsing a file into words.
 */
interface Tokenizer {

  /**
   * Parses provided file into words.
   *
   * @param file the file to parse.
   * @return a list of parsed tokens [Token].
   */
  fun tokenize(file: File): List<Token>
}
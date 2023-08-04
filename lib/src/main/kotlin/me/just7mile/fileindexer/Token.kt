package me.just7mile.fileindexer

/**
 * A token that contains the word and its location in the file.
 */
interface Token {
  /**
   * The word itself.
   */
  val word: String

  /**
   * The line in the file where the word is located.
   */
  val line: Int

  /**
   * The column in the file where the word is located.
   */
  val col: Int
}
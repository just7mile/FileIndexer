package me.just7mile.fileindexer

/**
 * Information about location of a word in a file.
 */
interface WordLocation {
  /**
   * The line in the file where the word is located.
   */
  val row: Int

  /**
   * The column in the file where the word is located.
   */
  val col: Int
}
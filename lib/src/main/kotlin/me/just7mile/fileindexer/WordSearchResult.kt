package me.just7mile.fileindexer

import java.io.File

/**
 * Single result of a word search.
 */
interface WordSearchResult {
  /**
   * The file where the searched word is located.
   */
  val file: File

  /**
   * Locations of the searched word in the [file].
   */
  val locations: List<WordLocation>
}
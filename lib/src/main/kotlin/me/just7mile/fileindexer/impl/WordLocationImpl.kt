package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.WordLocation

/**
 * Implementation of [WordLocation].
 */
data class WordLocationImpl(
  override val line: Int,
  override val col: Int,
) : WordLocation

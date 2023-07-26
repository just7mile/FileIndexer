package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.WordLocation

/**
 * Implementation of [WordLocation].
 */
internal data class WordLocationImpl(
  override val row: Int,
  override val col: Int,
) : WordLocation

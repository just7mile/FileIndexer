package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.WordLocation
import me.just7mile.fileindexer.WordSearchResult
import java.io.File

/**
 * Implementation of [WordSearchResult].
 */
internal data class WordSearchResultImpl(
  override val file: File,
  override val locations: List<WordLocation>,
) : WordSearchResult

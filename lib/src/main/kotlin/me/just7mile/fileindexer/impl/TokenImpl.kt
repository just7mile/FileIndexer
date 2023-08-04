package me.just7mile.fileindexer.impl

import me.just7mile.fileindexer.Token

/**
 * Implementation of [Token].
 */
internal data class TokenImpl(
  override val word: String,
  override val line: Int,
  override val col: Int,
) : Token

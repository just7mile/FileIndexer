package me.just7mile.fileindexer.impl.watcher

import me.just7mile.fileindexer.FileChangedEvent
import me.just7mile.fileindexer.FileChangedEventType
import java.nio.file.Path

/**
 * Implementation of [FileChangedEvent].
 */
internal data class FileChangedEventImpl(
  override val path: Path,
  override val type: FileChangedEventType,
) : FileChangedEvent
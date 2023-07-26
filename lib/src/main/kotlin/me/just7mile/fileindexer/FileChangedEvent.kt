package me.just7mile.fileindexer

import java.nio.file.Path

/**
 * Events fired by the [FileSystemWatchService].
 */
interface FileChangedEvent {
  /**
   * The path to the changed file.
   */
  val path: Path

  /**
   * The type of event.
   */
  val type: FileChangedEventType
}
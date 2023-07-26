package me.just7mile.fileindexer.impl.watcher

import kotlinx.coroutines.channels.Channel
import me.just7mile.fileindexer.FileChangedEvent
import java.nio.file.Path


internal sealed class WatcherBase(protected val path: Path, protected val channel: Channel<FileChangedEvent>) :
  Channel<FileChangedEvent> by channel
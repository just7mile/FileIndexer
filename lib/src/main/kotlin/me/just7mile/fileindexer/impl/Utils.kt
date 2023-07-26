package me.just7mile.fileindexer.impl

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Extension function for the [Path] to check if the path is a plain text file.
 */
internal fun Path.isPlainTextFile() = isRegularFile() && Files.probeContentType(this) == "text/plain"
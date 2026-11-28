package dev.marzahn.gloriana.logic.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

suspend fun ExportDestination.writeOnIo(content: (ByteSink) -> Unit): ExportWrite =
  withContext(Dispatchers.IO) { write(content) }

fun ByteSink.writeAll(bytes: ByteArray): Unit = write(bytes, 0, bytes.size)

package dev.marzahn.gloriana.logic.characters

interface ByteSource : AutoCloseable {
  fun read(into: ByteArray, offset: Int, length: Int): Int
}

fun interface ByteSink {
  fun write(bytes: ByteArray, offset: Int, length: Int)
}

sealed interface OpenedFile {
  class Opened(val source: ByteSource) : OpenedFile

  data object Unavailable : OpenedFile
}

interface PickedFile {
  val name: String

  fun open(): OpenedFile
}

interface ExportDestination {
  fun write(content: (ByteSink) -> Unit): ExportWrite
}

sealed interface ExportWrite {
  data object Written : ExportWrite

  data class Failed(val problem: ExportProblem) : ExportWrite
}

data class ExportProblem(val reason: ExportFailure, val incompleteFileLeft: Boolean)

enum class ExportFailure {
  StorageFull,
  CantWrite,
  StoredFileUnreadable,
}

class ExportContentFailure(val reason: ExportFailure, cause: Throwable) : RuntimeException(cause)

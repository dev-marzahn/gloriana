package dev.marzahn.gloriana.logic.files

import java.io.ByteArrayOutputStream
import java.io.IOException

class CorpusFile(override val name: String, val bytes: ByteArray) : PickedFile {
  override fun open(): OpenedFile = bytes.inputStream().let { input ->
    OpenedFile.Opened(
      object : ByteSource {
        override fun read(into: ByteArray, offset: Int, length: Int) = input.read(into, offset, length)

        override fun close() = input.close()
      },
    )
  }
}

class GoneFile(override val name: String) : PickedFile {
  override fun open(): OpenedFile = OpenedFile.Unavailable
}

class FailingToOpenFile(override val name: String) : PickedFile {
  override fun open(): OpenedFile = error("revoked")
}

class FailingToReadFile(override val name: String) : PickedFile {
  override fun open(): OpenedFile = OpenedFile.Opened(
    object : ByteSource {
      override fun read(into: ByteArray, offset: Int, length: Int): Int = throw IOException("unplugged")

      override fun close() = Unit
    },
  )
}

class MemoryDestination : ExportDestination {
  var bytes = ByteArray(0)
    private set

  override fun write(content: (ByteSink) -> Unit): ExportWrite {
    val out = ByteArrayOutputStream()
    content { bytes, offset, length -> out.write(bytes, offset, length) }
    bytes = out.toByteArray()
    return ExportWrite.Written
  }
}

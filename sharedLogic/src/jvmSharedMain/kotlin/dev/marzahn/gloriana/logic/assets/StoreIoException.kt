package dev.marzahn.gloriana.logic.assets

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

internal class StoreIoException(cause: IOException) : RuntimeException(cause)

internal fun <T> storeIo(work: () -> T): T = try {
  work()
} catch (error: IOException) {
  throw if (error.isStorageFull()) StorageFullException(error) else StoreIoException(error)
}

internal class StoreInputStream(input: InputStream) : FilterInputStream(input) {
  override fun read(): Int = storeIo { super.read() }

  override fun read(b: ByteArray, off: Int, len: Int): Int = storeIo { super.read(b, off, len) }

  override fun close() = storeIo { super.close() }
}

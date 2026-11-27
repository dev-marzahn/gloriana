package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.characters.ByteSink
import dev.marzahn.gloriana.logic.characters.ByteSource
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class DamagedFile(reason: String, cause: Throwable? = null) : RuntimeException(reason, cause)

internal class SourceReadException(cause: IOException) : RuntimeException(cause)

internal fun InputStream.asByteSource(): ByteSource = object : ByteSource {
  override fun read(into: ByteArray, offset: Int, length: Int): Int = this@asByteSource.read(into, offset, length)

  override fun close() = this@asByteSource.close()
}

internal fun OutputStream.asByteSink(): ByteSink = ByteSink { bytes, offset, length -> write(bytes, offset, length) }

internal fun ByteSource.asSourceStream(): InputStream = object : InputStream() {
  private val single = ByteArray(1)

  override fun read(): Int = if (read(single, 0, 1) == -1) -1 else single[0].toUByte().toInt()

  override fun read(b: ByteArray, off: Int, len: Int): Int = try {
    this@asSourceStream.read(b, off, len)
  } catch (error: IOException) {
    throw SourceReadException(error)
  }

  override fun close() {
    try {
      this@asSourceStream.close()
    } catch (_: IOException) {
      return
    }
  }
}

internal fun ByteSink.asOutputStream(): OutputStream = object : OutputStream() {
  override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

  override fun write(b: ByteArray, off: Int, len: Int) = this@asOutputStream.write(b, off, len)
}

internal class LimitedInputStream(private val input: InputStream, private val limit: Long) : InputStream() {
  var count = 0L
    private set

  val reachedLimit: Boolean
    get() = count >= limit

  override fun read(): Int {
    if (reachedLimit) return -1
    return input.read().also { if (it != -1) count++ }
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (reachedLimit) return -1
    return input.read(b, off, minOf(len.toLong(), limit - count).toInt()).also { if (it > 0) count += it }
  }

  fun drain(): LimitedInputStream {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var read = 0
    while (read != -1) read = read(buffer)
    return this
  }

  override fun close() = Unit
}

internal fun InputStream.readUpTo(count: Int): ByteArray = LimitedInputStream(this, count.toLong()).readBytes()

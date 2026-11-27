package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.storeIo
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.file.Path
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

private const val END_OF_DIRECTORY_SIGNATURE = 0x06054b50
private const val CENTRAL_ENTRY_SIGNATURE = 0x02014b50
private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
private const val END_OF_DIRECTORY_LENGTH = 22
private const val MAX_COMMENT_LENGTH = 0xFFFF
private const val CENTRAL_ENTRY_LENGTH = 46
private const val CENTRAL_METHOD_AT = 10
private const val CENTRAL_COMPRESSED_SIZE_AT = 20
private const val CENTRAL_SIZE_AT = 24
private const val CENTRAL_NAME_LENGTH_AT = 28
private const val CENTRAL_EXTRA_LENGTH_AT = 30
private const val CENTRAL_COMMENT_LENGTH_AT = 32
private const val CENTRAL_LOCAL_HEADER_AT = 42
private const val END_ENTRY_COUNT_AT = 10
private const val END_DIRECTORY_SIZE_AT = 12
private const val END_DIRECTORY_OFFSET_AT = 16
private const val LOCAL_HEADER_LENGTH = 30
private const val LOCAL_NAME_LENGTH_AT = 26
private const val LOCAL_EXTRA_LENGTH_AT = 28
private const val STORED = 0
private const val DEFLATED = 8
private const val UNSIGNED_SHORT = 0xFFFF
private const val UNSIGNED_INT = 0xFFFFFFFFL

internal class ZipEntryInfo(
  val name: String,
  val method: Int,
  val compressedSize: Long,
  val size: Long,
  val localHeaderOffset: Long,
) {
  val isDirectory: Boolean
    get() = name.endsWith("/")
}

internal class ZipArchive private constructor(private val access: RandomAccessFile) : AutoCloseable {
  val entries: List<ZipEntryInfo> = readCentralDirectory()

  companion object {
    fun open(file: Path): ZipArchive {
      val access = storeIo { RandomAccessFile(file.toFile(), "r") }
      var archive: ZipArchive? = null
      try {
        archive = ZipArchive(access)
        return archive
      } finally {
        if (archive == null) access.close()
      }
    }
  }

  fun open(entry: ZipEntryInfo): InputStream {
    val header = bytesAt(entry.localHeaderOffset, LOCAL_HEADER_LENGTH.toLong())
    if (header.int(0) != LOCAL_HEADER_SIGNATURE) throw DamagedFile("a zip entry has no local header")
    val dataStart = entry.localHeaderOffset + LOCAL_HEADER_LENGTH +
      header.short(LOCAL_NAME_LENGTH_AT) + header.short(LOCAL_EXTRA_LENGTH_AT)
    val raw = LimitedInputStream(Channels.newInputStream(access.channel.position(dataStart)), entry.compressedSize)
    return when (entry.method) {
      STORED -> DamageOnZipError(raw)
      DEFLATED -> DamageOnZipError(RawInflaterInputStream(raw))
      else -> throw DamagedFile("a zip entry uses compression method ${entry.method}")
    }
  }

  override fun close() = access.close()

  private fun readCentralDirectory(): List<ZipEntryInfo> {
    val end = findEndOfDirectory()
    val count = end.short(END_ENTRY_COUNT_AT)
    val directory = bytesAt(end.uInt(END_DIRECTORY_OFFSET_AT), end.uInt(END_DIRECTORY_SIZE_AT))
    var position = 0
    return List(count) {
      if (directory.int(position) != CENTRAL_ENTRY_SIGNATURE) throw DamagedFile("a zip central entry is broken")
      val nameLength = directory.short(position + CENTRAL_NAME_LENGTH_AT)
      val name = String(directory.array(), position + CENTRAL_ENTRY_LENGTH, nameLength, Charsets.UTF_8)
      val entry = ZipEntryInfo(
        name = name,
        method = directory.short(position + CENTRAL_METHOD_AT),
        compressedSize = directory.uInt(position + CENTRAL_COMPRESSED_SIZE_AT),
        size = directory.uInt(position + CENTRAL_SIZE_AT),
        localHeaderOffset = directory.uInt(position + CENTRAL_LOCAL_HEADER_AT),
      )
      val variableLength = nameLength + directory.short(position + CENTRAL_EXTRA_LENGTH_AT) +
        directory.short(position + CENTRAL_COMMENT_LENGTH_AT)
      position += CENTRAL_ENTRY_LENGTH + variableLength
      entry
    }
  }

  private fun findEndOfDirectory(): ByteBuffer {
    val length = storeIo { access.length() }
    val tailLength = minOf(length, (END_OF_DIRECTORY_LENGTH + MAX_COMMENT_LENGTH).toLong()).toInt()
    if (tailLength < END_OF_DIRECTORY_LENGTH) throw DamagedFile("too short for a zip")
    val tail = bytesAt(length - tailLength, tailLength.toLong())
    val start = (tailLength - END_OF_DIRECTORY_LENGTH downTo 0)
      .firstOrNull { tail.int(it) == END_OF_DIRECTORY_SIGNATURE }
      ?: throw DamagedFile("no zip end of central directory")
    return ByteBuffer.wrap(tail.array(), start, END_OF_DIRECTORY_LENGTH).slice().order(ByteOrder.LITTLE_ENDIAN)
  }

  private fun bytesAt(offset: Long, length: Long): ByteBuffer {
    val fileLength = storeIo { access.length() }
    val outside = offset < 0 || length < 0 || length > Int.MAX_VALUE || offset + length > fileLength
    if (outside) throw DamagedFile("a zip offset points past the file")
    val bytes = ByteArray(length.toInt())
    storeIo {
      access.seek(offset)
      access.readFully(bytes)
    }
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  }
}

private fun ByteBuffer.int(at: Int) = getInt(at)

private fun ByteBuffer.short(at: Int) = getShort(at).toInt() and UNSIGNED_SHORT

private fun ByteBuffer.uInt(at: Int) = getInt(at).toLong() and UNSIGNED_INT


private class RawInflaterInputStream(raw: InputStream, private val inflater: Inflater = Inflater(true)) :
  InflaterInputStream(SequenceInputStream(raw, ByteArrayInputStream(ByteArray(1))), inflater) {
  override fun close() {
    super.close()
    inflater.end()
  }
}

private class DamageOnZipError(input: InputStream) : FilterInputStream(input) {
  override fun read(): Int = guarded { super.read() }

  override fun read(b: ByteArray, off: Int, len: Int): Int = guarded { super.read(b, off, len) }

  private fun guarded(read: () -> Int): Int = try {
    read()
  } catch (error: IOException) {
    throw DamagedFile("a zip entry can't be read", error)
  }
}

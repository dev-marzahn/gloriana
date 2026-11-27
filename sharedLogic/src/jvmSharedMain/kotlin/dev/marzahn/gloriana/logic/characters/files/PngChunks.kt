package dev.marzahn.gloriana.logic.characters.files

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

internal val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
internal const val TEXT_CHUNK = "tEXt"
internal const val END_CHUNK = "IEND"
internal const val CCV3_KEYWORD = "ccv3"
internal const val CHARA_KEYWORD = "chara"
internal const val ASSET_KEYWORD_PREFIX = "chara-ext-asset_:"
private const val TYPE_LENGTH = 4
private const val MAX_KEYWORD_LENGTH = 79

internal class PngChunkReader(input: InputStream) {
  private val data = DataInputStream(input)

  init {
    if (!readExactly(PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) throw DamagedFile("not a PNG signature")
  }

  fun next(): PngChunk {
    val length = readInt()
    if (length < 0) throw DamagedFile("a PNG chunk length over 2^31")
    val typeBytes = readExactly(TYPE_LENGTH)
    return PngChunk(length, typeBytes, data)
  }

  private fun readInt(): Int = try {
    data.readInt()
  } catch (_: EOFException) {
    throw DamagedFile("the PNG ends early")
  }

  private fun readExactly(count: Int): ByteArray = ByteArray(count).also {
    try {
      data.readFully(it)
    } catch (_: EOFException) {
      throw DamagedFile("the PNG ends early")
    }
  }
}

internal class PngChunk(private val length: Int, private val typeBytes: ByteArray, private val data: DataInputStream) {
  val type = String(typeBytes, Charsets.ISO_8859_1)
  private val crc = CRC32().apply { update(typeBytes) }
  private val limited = LimitedInputStream(data, length.toLong())
  private val checked = CheckedInputStream(limited, crc)
  private val keywordBytes = ByteArrayOutputStream()
  val keyword: String? = if (type == TEXT_CHUNK) readKeyword() else null

  val textLength: Long
    get() = length.toLong() - keywordBytes.size()

  fun text(): InputStream = checked

  fun copyTo(out: OutputStream) {
    val header = DataOutputStream(out)
    header.writeInt(length)
    header.write(typeBytes)
    keywordBytes.writeTo(out)
    checked.copyTo(out)
    header.writeInt(finish())
  }

  fun finish(): Int {
    LimitedInputStream(checked, Long.MAX_VALUE).drain()
    if (limited.count != length.toLong()) throw DamagedFile("the PNG ends inside a chunk")
    val stored = readStoredCrc()
    if (stored != crc.value.toInt()) throw DamagedFile("a PNG chunk's CRC does not match")
    return stored
  }

  private fun readStoredCrc(): Int = try {
    data.readInt()
  } catch (_: EOFException) {
    throw DamagedFile("the PNG ends before a chunk's CRC")
  }

  private fun readKeyword(): String? {
    do {
      val next = checked.read()
      if (next != -1) keywordBytes.write(next)
    } while (next > 0 && keywordBytes.size() <= MAX_KEYWORD_LENGTH)
    val read = keywordBytes.toByteArray()
    return if (read.lastOrNull() == 0.toByte()) String(read, 0, read.size - 1, Charsets.ISO_8859_1) else null
  }
}

internal fun OutputStream.writePngChunk(type: String, body: ByteArray) =
  writePngChunk(type, body.size.toLong()) { it.write(body) }

internal fun OutputStream.writePngChunk(type: String, length: Long, body: (OutputStream) -> Unit) {
  val crc = CRC32()
  DataOutputStream(this).writeInt(Math.toIntExact(length))
  val checked = CheckedOutputStream(NotClosing(this), crc)
  checked.write(type.toByteArray(Charsets.ISO_8859_1))
  body(checked)
  checked.flush()
  DataOutputStream(this).writeInt(crc.value.toInt())
}

private class NotClosing(private val out: OutputStream) : OutputStream() {
  override fun write(b: Int) = out.write(b)

  override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

  override fun close() = Unit
}

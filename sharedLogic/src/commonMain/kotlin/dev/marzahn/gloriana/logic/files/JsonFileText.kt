package dev.marzahn.gloriana.logic.files

const val JSON_FILE_LIMIT_BYTES = 16L * 1024 * 1024

private const val READ_CHUNK_BYTES = 64 * 1024
private const val BYTE_ORDER_MARK_CODE = 0xFEFF
private val BYTE_ORDER_MARK = Char(BYTE_ORDER_MARK_CODE).toString()

sealed interface LimitedRead {
  class Read(val bytes: ByteArray) : LimitedRead

  data object TooLarge : LimitedRead
}

fun readWithin(limit: Long, read: (into: ByteArray, offset: Int, length: Int) -> Int): LimitedRead {
  val chunks = mutableListOf<ByteArray>()
  var total = 0L
  val buffer = ByteArray(READ_CHUNK_BYTES)
  while (true) {
    val count = read(buffer, 0, buffer.size)
    if (count < 0) break
    total += count
    if (total > limit) return LimitedRead.TooLarge
    chunks += buffer.copyOf(count)
  }
  val bytes = ByteArray(total.toInt())
  chunks.fold(0) { offset, chunk -> chunk.copyInto(bytes, offset).let { offset + chunk.size } }
  return LimitedRead.Read(bytes)
}

fun utf8Text(bytes: ByteArray): String? = try {
  bytes.decodeToString(throwOnInvalidSequence = true).withoutByteOrderMark()
} catch (_: CharacterCodingException) {
  null
}

fun String.withoutByteOrderMark(): String = removePrefix(BYTE_ORDER_MARK)

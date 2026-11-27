package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.characters.card.CardJsonRead
import dev.marzahn.gloriana.logic.characters.card.CardJsonReader
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val BYTE_ORDER_MARK_CODE = 0xFEFF
private val JSON_OPENERS = setOf('{', '[')

internal object CardJson {
  fun read(bytes: ByteArray): CardJsonRead = decodeUtf8(bytes)?.let(CardJsonReader::read) ?: CardJsonRead.Damaged

  fun startsLikeJson(start: ByteArray): Boolean =
    String(start, Charsets.UTF_8).removeByteOrderMark().trimStart().firstOrNull() in JSON_OPENERS

  fun readWithin(input: InputStream, limit: Long): ByteArray? =
    LimitedInputStream(input, limit + 1).readBytes().takeIf { it.size <= limit }

  private fun decodeUtf8(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(bytes))
      .toString()
      .removeByteOrderMark()
  } catch (_: CharacterCodingException) {
    null
  }

  private fun String.removeByteOrderMark() = removePrefix(Char(BYTE_ORDER_MARK_CODE).toString())
}

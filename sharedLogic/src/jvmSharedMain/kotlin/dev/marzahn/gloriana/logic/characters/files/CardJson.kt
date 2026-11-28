package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.characters.card.CardJsonRead
import dev.marzahn.gloriana.logic.characters.card.CardJsonReader
import dev.marzahn.gloriana.logic.files.utf8Text
import dev.marzahn.gloriana.logic.files.withoutByteOrderMark

private val JSON_OPENERS = setOf('{', '[')

internal object CardJson {
  fun read(bytes: ByteArray): CardJsonRead = utf8Text(bytes)?.let(CardJsonReader::read) ?: CardJsonRead.Damaged

  fun startsLikeJson(start: ByteArray): Boolean =
    String(start, Charsets.UTF_8).withoutByteOrderMark().trimStart().firstOrNull() in JSON_OPENERS
}

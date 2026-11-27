package dev.marzahn.gloriana.logic.characters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.ZipInputStream

private const val PNG_SIGNATURE_LENGTH = 8
private const val CHUNK_OVERHEAD = 12

object CardSources {
  fun cardJson(file: CorpusFile): JsonObject = Json.parseToJsonElement(cardText(file.bytes)).jsonObject

  fun pngTexts(png: ByteArray): Map<String, ByteArray> {
    val texts = linkedMapOf<String, ByteArray>()
    var position = PNG_SIGNATURE_LENGTH
    while (position < png.size) {
      val length = ByteBuffer.wrap(png, position, 4).int
      val type = String(png, position + 4, 4, Charsets.ISO_8859_1)
      if (type == "tEXt") {
        val body = png.copyOfRange(position + 8, position + 8 + length)
        val split = body.indexOf(0)
        texts[String(body, 0, split, Charsets.ISO_8859_1)] = body.copyOfRange(split + 1, body.size)
      }
      position += CHUNK_OVERHEAD + length
    }
    return texts
  }

  fun pngCard(png: ByteArray): JsonObject {
    val texts = pngTexts(png)
    val text = listOf("ccv3", "chara").firstNotNullOf { keyword ->
      texts[keyword]?.let { runCatching { String(Base64.getMimeDecoder().decode(it)) }.getOrNull() }
        ?.takeIf { runCatching { Json.parseToJsonElement(it).jsonObject }.isSuccess }
    }
    return Json.parseToJsonElement(text).jsonObject
  }

  private fun cardText(bytes: ByteArray): String = when {
    bytes[0] == 0x89.toByte() -> Json.encodeToString(pngCard(bytes))
    bytes[0] == 'P'.code.toByte() -> ZipInputStream(bytes.inputStream()).use { zip ->
      generateSequence { zip.nextEntry }.first { it.name == "card.json" }
      zip.readBytes().decodeToString()
    }
    else -> bytes.decodeToString()
  }
}

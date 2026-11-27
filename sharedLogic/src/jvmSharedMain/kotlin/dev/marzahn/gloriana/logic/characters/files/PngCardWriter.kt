package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.assets.AssetStore
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

private const val BASE64_GROUP_BYTES = 3
private const val BASE64_GROUP_CHARACTERS = 4
private const val EMBEDDED_PATH_LENGTH = 32

internal fun embeddedPath(hash: AssetHash) = hash.hex.take(EMBEDDED_PATH_LENGTH)

internal class PngCardWriter(private val store: AssetStore) {
  fun write(avatar: InputStream, cards: Pair<String, String>, embedded: Set<AssetHash>, out: OutputStream) {
    val chunks = PngChunkReader(avatar)
    out.write(PNG_SIGNATURE)
    var chunk = chunks.next()
    while (chunk.type != END_CHUNK) {
      if (chunk.keyword.isCardKeyword()) chunk.finish() else chunk.copyTo(out)
      chunk = chunks.next()
    }
    out.writeTextChunk(CHARA_KEYWORD, cards.first)
    out.writeTextChunk(CCV3_KEYWORD, cards.second)
    embedded.forEach { out.writeAssetChunk(it) }
    chunk.copyTo(out)
  }

  private fun String?.isCardKeyword() =
    this == CCV3_KEYWORD || this == CHARA_KEYWORD || this?.startsWith(ASSET_KEYWORD_PREFIX) == true

  private fun OutputStream.writeTextChunk(keyword: String, json: String) {
    val text = Base64.getEncoder().encode(json.toByteArray(Charsets.UTF_8))
    writePngChunk(TEXT_CHUNK, keyword.toByteArray(Charsets.ISO_8859_1) + 0.toByte() + text)
  }

  private fun OutputStream.writeAssetChunk(hash: AssetHash) {
    val keyword = (ASSET_KEYWORD_PREFIX + embeddedPath(hash)).toByteArray(Charsets.ISO_8859_1)
    val size = store.size(hash)
    val encodedLength = (size + BASE64_GROUP_BYTES - 1) / BASE64_GROUP_BYTES * BASE64_GROUP_CHARACTERS
    writePngChunk(TEXT_CHUNK, keyword.size + 1 + encodedLength) { body ->
      body.write(keyword)
      body.write(0)
      Base64.getEncoder().wrap(NotClosingOutput(body)).use { encoder -> store.open(hash).use { it.copyTo(encoder) } }
    }
  }
}

private class NotClosingOutput(private val out: OutputStream) : OutputStream() {
  override fun write(b: Int) = out.write(b)

  override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

  override fun close() = Unit
}

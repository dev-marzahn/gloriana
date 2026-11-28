package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetStaging
import dev.marzahn.gloriana.logic.assets.StagedAsset
import dev.marzahn.gloriana.logic.characters.AssetSource
import dev.marzahn.gloriana.logic.characters.CardFileRead
import dev.marzahn.gloriana.logic.characters.CharacterAsset
import dev.marzahn.gloriana.logic.characters.DamagedAs
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.ImportedCard
import dev.marzahn.gloriana.logic.characters.SizeLimit
import dev.marzahn.gloriana.logic.characters.card.CardAssetEntry
import dev.marzahn.gloriana.logic.characters.card.CardJsonRead
import dev.marzahn.gloriana.logic.characters.card.ParsedCard
import dev.marzahn.gloriana.logic.files.JSON_FILE_LIMIT_BYTES
import java.io.IOException
import java.io.InputStream
import java.util.Base64

internal const val PNG_ASSET_URI_PREFIX = "__asset:"
internal const val DEFAULT_ASSET_URI = "ccdefault:"
internal const val ICON_ASSET_TYPE = "icon"

private sealed interface ChunkScan {
  data object Complete : ChunkScan

  data object CardTooLarge : ChunkScan
}

internal class PngCardReader(private val staging: Staging) {
  private val cardTexts = mutableMapOf<String, ByteArray>()
  private val assetFiles = mutableMapOf<String, StagedAsset>()

  fun read(input: InputStream): CardFileRead {
    var scan: ChunkScan = ChunkScan.Complete
    val avatar = staging.file { avatarFile ->
      val chunks = PngChunkReader(input)
      avatarFile.write(PNG_SIGNATURE)
      scan = scanChunks(chunks, avatarFile)
    }
    val decoded = listOf(CCV3_KEYWORD, CHARA_KEYWORD).filter { it in cardTexts }
      .map { keyword -> decodeCardText(cardTexts.getValue(keyword)) }
    val tooLarge = scan == ChunkScan.CardTooLarge || decoded.any { (it?.size ?: 0) > JSON_FILE_LIMIT_BYTES }
    if (tooLarge) return CardFileRead.Failed(ImportFailure.TooLarge(SizeLimit.CardJson))
    return when (val card = chooseCard(decoded)) {
      is CardJsonRead.Card -> CardFileRead.Card(imported(card.card, avatar))
      CardJsonRead.Damaged -> CardFileRead.Failed(ImportFailure.Damaged(DamagedAs.Png))
      CardJsonRead.NotACard -> CardFileRead.Failed(ImportFailure.NotACharacterCard)
    }
  }

  private fun scanChunks(chunks: PngChunkReader, avatar: AssetStaging): ChunkScan {
    var chunk = chunks.next()
    while (true) {
      val keyword = chunk.keyword
      when {
        keyword == CCV3_KEYWORD || keyword == CHARA_KEYWORD -> {
          if (chunk.textLength > CardLimits.CARD_JSON_BASE64_CHARACTERS) return ChunkScan.CardTooLarge
          cardTexts[keyword] = chunk.text().readBytes()
          chunk.finish()
        }
        keyword != null && keyword.startsWith(ASSET_KEYWORD_PREFIX) -> {
          val path = keyword.removePrefix(ASSET_KEYWORD_PREFIX)
          assetFiles[path] = staging.file { file -> decodeBase64(chunk.text(), file) }
          chunk.finish()
        }
        else -> chunk.copyTo(avatar)
      }
      if (chunk.type == END_CHUNK) return ChunkScan.Complete
      chunk = chunks.next()
    }
  }

  private fun decodeBase64(text: InputStream, out: AssetStaging) {
    try {
      Base64.getMimeDecoder().wrap(text).copyTo(out)
    } catch (_: IOException) {
      throw DamagedFile("an asset chunk is not base64")
    } catch (_: IllegalArgumentException) {
      throw DamagedFile("an asset chunk is not base64")
    }
  }

  private fun chooseCard(decoded: List<ByteArray?>): CardJsonRead {
    val found = decoded.map { json -> json?.let(CardJson::read) ?: CardJsonRead.Damaged }
    val unreadable = if (CardJsonRead.Damaged in found) CardJsonRead.Damaged else CardJsonRead.NotACard
    return found.firstOrNull { it is CardJsonRead.Card } ?: unreadable
  }

  private fun decodeCardText(base64: ByteArray): ByteArray? = try {
    Base64.getMimeDecoder().decode(base64)
  } catch (_: IllegalArgumentException) {
    null
  }

  private fun imported(card: ParsedCard, avatar: StagedAsset): ImportedCard {
    val assets = card.assets.mapNotNull(::resolve)
    return ImportedCard.WithFiles(
      parsed = card,
      avatar = avatar.hash,
      assets = assets.map { it.asset },
      staged = staging.keep(assets.mapNotNull { it.file } + avatar),
    )
  }

  private fun resolve(entry: CardAssetEntry): ResolvedAsset? {
    if (entry.type == ICON_ASSET_TYPE && entry.uri == DEFAULT_ASSET_URI) return null
    val staged = entry.uri?.takeIf { it.startsWith(PNG_ASSET_URI_PREFIX) }
      ?.let { assetFiles[it.removePrefix(PNG_ASSET_URI_PREFIX)] }
    return ResolvedAsset(entry.toAsset(staged), staged)
  }
}

internal class ResolvedAsset(val asset: CharacterAsset, val file: StagedAsset?)

internal fun CardAssetEntry.toAsset(stored: StagedAsset?): CharacterAsset {
  val source = when {
    stored != null -> AssetSource.Stored(stored.hash)
    uri != null -> AssetSource.Uri(uri)
    else -> AssetSource.Unnamed
  }
  return CharacterAsset(type, name, extension, source, otherKeys)
}

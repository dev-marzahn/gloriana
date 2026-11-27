package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.assets.StorageFullException
import dev.marzahn.gloriana.logic.assets.StoreIoException
import dev.marzahn.gloriana.logic.characters.AssetSource
import dev.marzahn.gloriana.logic.characters.AvatarChange
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.characters.CardForExport
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.CardWriter
import dev.marzahn.gloriana.logic.characters.CharacterAsset
import dev.marzahn.gloriana.logic.characters.ExportResult
import dev.marzahn.gloriana.logic.characters.card.CardAssetEntry
import dev.marzahn.gloriana.logic.characters.card.CardJsonWriter
import dev.marzahn.gloriana.logic.characters.card.CardSpec
import dev.marzahn.gloriana.logic.characters.card.CardToWrite
import dev.marzahn.gloriana.logic.files.ExportContentFailure
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.logic.files.ExportWrite
import kotlinx.serialization.json.Json

internal class JvmCardWriter(private val store: AssetStore, transcoder: AvatarTranscoder) : CardWriter {
  private val avatars = PngAvatars(store, transcoder)

  override fun write(card: CardForExport, format: CardFormat, destination: ExportDestination): ExportResult = try {
    writeChecked(card, format, destination)
  } catch (_: StoreIoException) {
    UNREADABLE
  } catch (_: StorageFullException) {
    UNREADABLE
  } catch (_: DamagedFile) {
    UNREADABLE
  }

  private fun writeChecked(card: CardForExport, format: CardFormat, destination: ExportDestination): ExportResult {
    val stored = card.character.assets.mapNotNull { (it.source as? AssetSource.Stored)?.hash }
    val needed = if (format == CardFormat.Png) listOfNotNull(card.character.avatar) + stored else emptyList()
    if (needed.any { !store.exists(it) }) return UNREADABLE
    return when (format) {
      CardFormat.Json -> writeJson(card, destination)
      CardFormat.Png -> writePng(card, stored.toSet(), destination)
    }
  }

  private fun writeJson(card: CardForExport, destination: ExportDestination): ExportResult {
    val assets = card.character.assets.filterNot { it.source is AssetSource.Stored }
      .map { it.entry(uri = it.uriOrNull()) }
    val json = cardJson(card, CardSpec.V3, assets).toByteArray(Charsets.UTF_8)
    return destination.write { sink -> sink.write(json, 0, json.size) }.toResult(avatarChange = null)
  }

  private fun writePng(card: CardForExport, embedded: Set<AssetHash>, destination: ExportDestination): ExportResult {
    val avatar = avatars.of(card.character)
    val assets = listOf(PNG_AVATAR_ENTRY) + card.character.assets.mapNotNull(::pngEntry)
    val cards = cardJson(card, CardSpec.V2, assets) to cardJson(card, CardSpec.V3, assets)
    return destination.write { sink ->
      storedFilesRead {
        sink.asOutputStream().buffered().use { out ->
          avatar.open().use { PngCardWriter(store).write(it, cards, embedded, out) }
        }
      }
    }.toResult(avatar.change)
  }

  private fun pngEntry(asset: CharacterAsset): CardAssetEntry? = when (val source = asset.source) {
    is AssetSource.Stored -> asset.entry(uri = PNG_ASSET_URI_PREFIX + embeddedPath(source.hash))
    is AssetSource.Uri -> asset.entry(uri = source.uri)
      .takeUnless { asset.type == ICON_ASSET_TYPE && source.uri == DEFAULT_ASSET_URI }
    AssetSource.Unnamed -> asset.entry(uri = null)
  }

  private fun cardJson(card: CardForExport, spec: CardSpec, assets: List<CardAssetEntry>): String {
    val toWrite = CardToWrite(card.character.fields, card.character.pronouns, card.verbatim, assets)
    return Json.encodeToString(CardJsonWriter.write(toWrite, spec))
  }

  private fun ExportWrite.toResult(avatarChange: AvatarChange?) = when (this) {
    ExportWrite.Written -> ExportResult.Saved(avatarChange)
    is ExportWrite.Failed -> ExportResult.NotSaved(problem)
  }
}

private val UNREADABLE =
  ExportResult.NotSaved(ExportProblem(ExportFailure.StoredFileUnreadable, incompleteFileLeft = false))

@Suppress("TooGenericExceptionCaught")
private fun storedFilesRead(write: () -> Unit) {
  try {
    write()
  } catch (failure: Exception) {
    val storedFileFailed = failure is StoreIoException || failure is StorageFullException || failure is DamagedFile
    throw if (storedFileFailed) ExportContentFailure(ExportFailure.StoredFileUnreadable, failure) else failure
  }
}

private fun CharacterAsset.uriOrNull() = (source as? AssetSource.Uri)?.uri

private fun CharacterAsset.entry(uri: String?) = CardAssetEntry(type, name, uri, extension, otherKeys)

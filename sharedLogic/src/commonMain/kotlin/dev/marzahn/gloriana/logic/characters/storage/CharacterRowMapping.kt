package dev.marzahn.gloriana.logic.characters.storage

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.characters.AssetSource
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterAsset
import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.CharacterSummary
import dev.marzahn.gloriana.logic.characters.KeptCardFields
import dev.marzahn.gloriana.logic.characters.Pronouns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

private val UNICODE_WHITE_SPACE = setOf(
  0x0020, 0x0085, 0x00A0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000,
) + (0x0009..0x000D) + (0x2000..0x200A)

internal fun matchKey(value: String): String = value.trim { it.code in UNICODE_WHITE_SPACE }

internal data class CharacterContent(
  val fields: CharacterFields,
  val avatar: AssetHash?,
  val assets: List<CharacterAsset>,
  val pronouns: Pronouns,
  val kept: KeptCardFields,
  val cardJson: String,
)

internal data class RowStamp(val id: Uuid, val importedAt: Long, val updatedAt: Long)

internal fun CharacterContent.toEntity(stamp: RowStamp) = CharacterEntity(
  id = stamp.id,
  name = fields.name,
  nickname = fields.nickname,
  description = fields.description,
  personality = fields.personality,
  scenario = fields.scenario,
  firstMessage = fields.firstMessage,
  exampleMessages = fields.exampleMessages,
  creator = fields.creator,
  characterVersion = fields.characterVersion,
  creatorNotes = fields.creatorNotes,
  systemPrompt = fields.systemPrompt,
  postHistoryInstructions = fields.postHistoryInstructions,
  characterNote = fields.characterNote,
  pronouns = pronouns,
  avatarHash = avatar?.hex,
  keptFields = kept,
  cardJson = cardJson,
  nameKey = matchKey(fields.name),
  creatorKey = matchKey(fields.creator),
  importedAt = stamp.importedAt,
  updatedAt = stamp.updatedAt,
)

internal fun CharacterContent.greetingRows(characterId: Uuid, updatedAt: Long, newId: () -> Uuid) =
  fields.alternateGreetings.mapIndexed { position, text ->
    CharacterGreetingEntity(newId(), characterId, GreetingKind.Alternate, position, text, updatedAt)
  } + fields.groupOnlyGreetings.mapIndexed { position, text ->
    CharacterGreetingEntity(newId(), characterId, GreetingKind.GroupOnly, position, text, updatedAt)
  }

internal fun CharacterContent.assetRows(characterId: Uuid, updatedAt: Long, newId: () -> Uuid) =
  assets.mapIndexed { position, asset ->
    val source = asset.source
    CharacterAssetEntity(
      id = newId(),
      characterId = characterId,
      position = position,
      type = asset.type,
      name = asset.name,
      extension = asset.extension,
      hash = (source as? AssetSource.Stored)?.hash?.hex,
      uri = (source as? AssetSource.Uri)?.uri,
      otherKeys = asset.otherKeys,
      updatedAt = updatedAt,
    )
  }

internal fun CharacterRows.verbatimCard(): JsonObject = Json.parseToJsonElement(character.cardJson).jsonObject

internal fun CharacterRows.toCharacter(): Character {
  val row = character
  return Character(
    id = row.id,
    importedAt = row.importedAt,
    fields = CharacterFields(
      name = row.name,
      nickname = row.nickname,
      description = row.description,
      personality = row.personality,
      scenario = row.scenario,
      firstMessage = row.firstMessage,
      alternateGreetings = greetings.texts(GreetingKind.Alternate),
      groupOnlyGreetings = greetings.texts(GreetingKind.GroupOnly),
      exampleMessages = row.exampleMessages,
      creator = row.creator,
      characterVersion = row.characterVersion,
      creatorNotes = row.creatorNotes,
      systemPrompt = row.systemPrompt,
      postHistoryInstructions = row.postHistoryInstructions,
      characterNote = row.characterNote,
    ),
    avatar = row.avatarHash?.let(::AssetHash),
    assets = assets.sortedBy { it.position }.map(CharacterAssetEntity::toAsset),
    pronouns = row.pronouns,
    keptFields = row.keptFields,
  )
}

internal fun CharacterSummaryRow.toSummary() = CharacterSummary(id, name, avatarHash?.let(::AssetHash))

private fun List<CharacterGreetingEntity>.texts(kind: GreetingKind) =
  filter { it.kind == kind }.sortedBy { it.position }.map { it.text }

private fun CharacterAssetEntity.toAsset(): CharacterAsset {
  val source = when {
    hash != null -> AssetSource.Stored(AssetHash(hash))
    uri != null -> AssetSource.Uri(uri)
    else -> AssetSource.Unnamed
  }
  return CharacterAsset(type, name, extension, source, otherKeys)
}

package dev.marzahn.gloriana.logic.characters.card

import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.CharacterNote
import dev.marzahn.gloriana.logic.characters.KeptCardFields
import dev.marzahn.gloriana.logic.characters.Pronouns
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val ASSET_TYPE_KEY = "type"
private const val ASSET_URI_KEY = "uri"
private const val ASSET_NAME_KEY = "name"
private const val ASSET_EXTENSION_KEY = "ext"
private val ASSET_KEYS = setOf(ASSET_TYPE_KEY, ASSET_URI_KEY, ASSET_NAME_KEY, ASSET_EXTENSION_KEY)

internal data class CardAssetEntry(
  val type: String,
  val name: String,
  val uri: String?,
  val extension: String,
  val otherKeys: JsonObject = JsonObject(emptyMap()),
) {
  fun toJson() = JsonObject(
    otherKeys + listOfNotNull(
      ASSET_TYPE_KEY to JsonPrimitive(type),
      uri?.let { ASSET_URI_KEY to JsonPrimitive(it) },
      ASSET_NAME_KEY to JsonPrimitive(name),
      ASSET_EXTENSION_KEY to JsonPrimitive(extension),
    ),
  )
}

internal data class ParsedCard(
  val fields: CharacterFields,
  val assets: List<CardAssetEntry>,
  val pronouns: Pronouns,
  val kept: KeptCardFields,
  val verbatim: JsonObject,
)

internal sealed interface CardJsonRead {
  data class Card(val card: ParsedCard) : CardJsonRead

  data object Damaged : CardJsonRead

  data object NotACard : CardJsonRead
}

internal object CardJsonReader {
  fun read(text: String): CardJsonRead {
    val root = try {
      Json.parseToJsonElement(text)
    } catch (_: SerializationException) {
      null
    }
    return when (root) {
      null -> CardJsonRead.Damaged
      is JsonObject -> read(root)
      else -> CardJsonRead.NotACard
    }
  }

  private fun read(root: JsonObject): CardJsonRead = when (val shape = CardShape.of(root)) {
    is CardShape.WithData -> CardJsonRead.Card(parsed(shape))
    is CardShape.Ccv1 -> when {
      root.hasKnownSpec() -> CardJsonRead.Damaged
      passesCcv1Test(root) -> CardJsonRead.Card(parsed(shape))
      else -> CardJsonRead.NotACard
    }
  }

  private fun passesCcv1Test(root: JsonObject) =
    root.string(CardTextField.Name.key) != null &&
      CardTextField.entries.any { it.inCcv1 && it != CardTextField.Name && root.string(it.key) != null } &&
      "entries" !in root &&
      "prompts" !in root

  private fun parsed(shape: CardShape): ParsedCard {
    val fields = shape.fields
    val readsCcv3Keys = shape is CardShape.WithData
    val extensions = fields.objectAt(EXTENSIONS_KEY).takeIf { readsCcv3Keys }
    return ParsedCard(
      fields = characterFields(
        text = { field -> if (field.inCcv1 || readsCcv3Keys) fields.string(field.key).orEmpty() else "" },
        list = { field -> if (readsCcv3Keys) fields.strings(field.key) else emptyList() },
        note = extensions?.objectAt(DEPTH_PROMPT_KEY)?.let(::characterNote),
      ),
      assets = (fields[ASSETS_KEY] as? JsonArray).orEmpty().takeIf { readsCcv3Keys }.orEmpty().mapNotNull(::assetEntry),
      pronouns = extensions?.objectAt(GLORIANA_KEY)?.objectAt(PRONOUNS_KEY)?.let { forms ->
        pronouns { forms.string(it.key) }
      } ?: Pronouns.None,
      kept = keptFields(shape),
      verbatim = shape.root,
    )
  }

  private fun characterNote(depthPrompt: JsonObject) = CharacterNote(
    text = depthPrompt.string(DepthPromptKey.Prompt.key).orEmpty(),
    depth = (depthPrompt[DepthPromptKey.Depth.key] as? JsonPrimitive)?.intOrNull ?: 0,
    role = depthPrompt.string(DepthPromptKey.Role.key) ?: DEFAULT_NOTE_ROLE,
  )

  private fun assetEntry(element: JsonElement): CardAssetEntry? = (element as? JsonObject)?.let { asset ->
    CardAssetEntry(
      type = asset.string(ASSET_TYPE_KEY).orEmpty(),
      name = asset.string(ASSET_NAME_KEY).orEmpty(),
      uri = asset.string(ASSET_URI_KEY),
      extension = asset.string(ASSET_EXTENSION_KEY).orEmpty(),
      otherKeys = JsonObject(asset.filterKeys { it !in ASSET_KEYS }),
    )
  }

  private fun keptFields(shape: CardShape): KeptCardFields {
    val fields = shape.fields
    return KeptCardFields(
      tags = fields.strings(TAGS_KEY),
      source = fields.strings("source"),
      creationDate = (fields["creation_date"] as? JsonPrimitive)?.longOrNull,
      modificationDate = (fields["modification_date"] as? JsonPrimitive)?.longOrNull,
      creatorNotesInOtherLanguages = fields.objectAt("creator_notes_multilingual").orEmpty()
        .mapNotNull { (language, notes) -> notes.asString()?.let { language to it } }
        .toMap(),
      hasEmbeddedLorebook = fields[EMBEDDED_LOREBOOK_KEY] is JsonObject,
    )
  }
}

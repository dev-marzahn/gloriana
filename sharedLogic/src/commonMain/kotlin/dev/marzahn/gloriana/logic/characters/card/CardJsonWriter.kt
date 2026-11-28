package dev.marzahn.gloriana.logic.characters.card

import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.CharacterNote
import dev.marzahn.gloriana.logic.characters.NoteRole
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.logic.json.objectAt
import dev.marzahn.gloriana.logic.json.orNone
import dev.marzahn.gloriana.logic.json.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val SPEC_KEY = "spec"
private const val SPEC_VERSION_KEY = "spec_version"
private const val DATA_KEY = "data"

internal enum class CardSpec(val spec: String, val version: String) {
  V3(SPEC_V3, "3.0"),
  V2(SPEC_V2, "2.0"),
}

internal data class CardToWrite(
  val fields: CharacterFields,
  val pronouns: Pronouns,
  val verbatim: JsonObject,
  val assets: List<CardAssetEntry>,
)

internal object CardJsonWriter {
  fun write(card: CardToWrite, spec: CardSpec): JsonObject {
    val shape = CardShape.of(card.verbatim)
    val keptData = (shape as? CardShape.WithData)?.fields.orNone()
    val data = keptData + columns(card, shape, keptData)
    val ccv1Copies = CardTextField.entries.filter { it.inCcv1 }.associate { it.key to text(it.of(card.fields)) }
    return JsonObject(
      card.verbatim + ccv1Copies + mapOf(
        SPEC_KEY to JsonPrimitive(spec.spec),
        SPEC_VERSION_KEY to JsonPrimitive(spec.version),
        DATA_KEY to JsonObject(data),
      ),
    )
  }

  private fun columns(card: CardToWrite, shape: CardShape, keptData: JsonObject): Map<String, JsonElement> =
    CardTextField.entries.associate { it.key to text(it.of(card.fields)) } +
      CardListField.entries.associate { field -> field.key to JsonArray(field.of(card.fields).map(::text)) } +
      mapOf(
        TAGS_KEY to (shape.fields[TAGS_KEY] as? JsonArray ?: JsonArray(emptyList())),
        EXTENSIONS_KEY to extensions(keptData.objectAt(EXTENSIONS_KEY).orNone(), card),
        ASSETS_KEY to JsonArray(card.assets.map(CardAssetEntry::toJson)),
      )

  private fun extensions(kept: JsonObject, card: CardToWrite): JsonObject {
    val extensions = kept.toMutableMap()
    val note = card.fields.characterNote
    if (note == null) {
      extensions.remove(DEPTH_PROMPT_KEY)
    } else {
      extensions[DEPTH_PROMPT_KEY] = depthPrompt(kept.objectAt(DEPTH_PROMPT_KEY).orNone(), note)
    }
    val gloriana = kept.objectAt(GLORIANA_KEY).orEmpty().toMutableMap()
    if (card.pronouns.isEmpty) gloriana.remove(PRONOUNS_KEY) else gloriana[PRONOUNS_KEY] = pronounsJson(card.pronouns)
    if (gloriana.isEmpty()) extensions.remove(GLORIANA_KEY) else extensions[GLORIANA_KEY] = JsonObject(gloriana)
    return JsonObject(extensions)
  }

  private fun depthPrompt(kept: JsonObject, note: CharacterNote) = JsonObject(
    kept + mapOf(
      DepthPromptKey.Prompt.key to text(note.text),
      DepthPromptKey.Depth.key to JsonPrimitive(note.depth),
      DepthPromptKey.Role.key to text(roleOnCard(kept, note.role)),
    ),
  )

  private fun roleOnCard(kept: JsonObject, role: NoteRole): String =
    kept.string(DepthPromptKey.Role.key)?.takeIf { NoteRole.fromCard(it) == role } ?: role.cardValue

  private fun pronounsJson(pronouns: Pronouns) = JsonObject(
    PronounKey.entries.mapNotNull { key -> key.of(pronouns)?.let { key.key to text(it) } }.toMap(),
  )

  private fun text(value: String) = JsonPrimitive(value)
}

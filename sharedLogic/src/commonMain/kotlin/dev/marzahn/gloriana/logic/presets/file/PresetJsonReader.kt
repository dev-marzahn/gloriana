package dev.marzahn.gloriana.logic.presets.file

import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.logic.presets.PresetSampler
import dev.marzahn.gloriana.logic.presets.PresetSamplers
import dev.marzahn.gloriana.logic.presets.WrapperPrompts
import dev.marzahn.gloriana.logic.presets.accepts
import dev.marzahn.gloriana.logic.storage.IdFactory
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.uuid.Uuid

internal sealed interface PresetFileRead {
  class Read(
    val content: PresetContent,
    val source: JsonObject,
    val samplers: PresetSamplers,
    val credentialsSkipped: Boolean,
  ) : PresetFileRead

  data object Damaged : PresetFileRead

  data object NotAPreset : PresetFileRead
}

internal class PresetJsonReader(private val ids: IdFactory) {
  fun read(text: String): PresetFileRead = when (val root = parsed(text)) {
    null -> PresetFileRead.Damaged
    is JsonObject -> when (root[PROMPTS_KEY]) {
      null -> PresetFileRead.NotAPreset
      is JsonArray -> read(root)
      else -> PresetFileRead.Damaged
    }
    else -> PresetFileRead.NotAPreset
  }

  private fun read(root: JsonObject): PresetFileRead.Read {
    val view = PresetFileView(root)
    return PresetFileRead.Read(
      content = PresetContent(rows = rows(view), wrappers = WrapperPrompts.of(view.wrappers)),
      source = JsonObject(root - keysNeverStored),
      samplers = samplers(root),
      credentialsSkipped = credentialKeys.any { root[it].isSet() },
    )
  }

  private fun rows(view: PresetFileView): List<PresetRow> {
    val entries = view.entries.map { it.fields.entry(ids.newId(), it.identifier, it.enabled) }
    val groupsAt = view.groups.groupBy({ it.at }, { PresetGroup(ids.newId(), it.name) })
    return (0..entries.size).flatMap { index -> groupsAt[index].orEmpty() + listOfNotNull(entries.getOrNull(index)) }
  }

  private fun samplers(root: JsonObject) = PresetSamplers(
    allPresetSamplers.mapNotNull { sampler ->
      (root[sampler.fileKey] as? JsonPrimitive)
        ?.takeUnless { it.isString }
        ?.doubleOrNull
        ?.takeIf { sampler.accepts(it) }
        ?.let { sampler to it }
    }.toMap(),
  )
}

private fun PromptFields.entry(id: Uuid, identifier: String, enabled: Boolean) =
  PresetEntry(id, identifier, name, text, filledByApp, enabled, role, placement)

private fun JsonElement?.isSet(): Boolean = when (this) {
  null, JsonNull -> false
  is JsonPrimitive -> if (isString) content.isNotEmpty() else content != "false" && content != "0"
  else -> true
}

private fun parsed(text: String): JsonElement? = try {
  Json.parseToJsonElement(text)
} catch (_: SerializationException) {
  null
}

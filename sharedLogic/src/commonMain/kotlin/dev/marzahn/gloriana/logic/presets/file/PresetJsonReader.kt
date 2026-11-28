package dev.marzahn.gloriana.logic.presets.file

import dev.marzahn.gloriana.logic.json.parsedElement
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.logic.presets.PresetSamplers
import dev.marzahn.gloriana.logic.presets.WrapperPrompts
import dev.marzahn.gloriana.logic.storage.IdFactory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
  fun read(text: String): PresetFileRead = when (val root = parsedElement(text)) {
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

  private fun samplers(root: JsonObject) = PresetSamplers.read { sampler -> root[sampler.fileKey] as? JsonPrimitive }
}

private fun PromptFields.entry(id: Uuid, identifier: String, enabled: Boolean) =
  PresetEntry(id, identifier, name, text, filledByApp, enabled, role, placement)

private fun JsonElement?.isSet(): Boolean = when (this) {
  null, JsonNull -> false
  is JsonPrimitive -> if (isString) content.isNotEmpty() else content != "false" && content != "0"
  else -> true
}

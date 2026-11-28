package dev.marzahn.gloriana.logic.presets.file

import dev.marzahn.gloriana.logic.json.int
import dev.marzahn.gloriana.logic.json.quotedOrBareBoolean
import dev.marzahn.gloriana.logic.json.string
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.PresetEntry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val NAME_KEY = "name"
private const val CONTENT_KEY = "content"
private const val MARKER_KEY = "marker"
private const val ROLE_KEY = "role"
private const val POSITION_KEY = "injection_position"
private const val DEPTH_KEY = "injection_depth"
private const val RELATIVE_POSITION = 0
private const val IN_CHAT_POSITION = 1
private const val PINNED_DEFAULT_DEPTH = 4

private val EntryRole.fileValue: String
  get() = when (this) {
    EntryRole.System -> "system"
    EntryRole.User -> "user"
    EntryRole.Assistant -> "assistant"
  }

private fun roleOf(fileValue: String?): EntryRole =
  EntryRole.entries.firstOrNull { it.fileValue == fileValue } ?: EntryRole.System

internal data class PromptFields(
  val name: String,
  val text: String,
  val filledByApp: Boolean,
  val role: EntryRole,
  val placement: EntryPlacement,
) {
  fun overlaidOn(prompt: JsonObject, identifier: String): JsonObject {
    val kept = of(prompt, identifier)
    val changes = buildMap<String, JsonElement> {
      if (name != kept.name) put(NAME_KEY, JsonPrimitive(name))
      if (text != kept.text) put(CONTENT_KEY, JsonPrimitive(text))
      if (filledByApp != kept.filledByApp) put(MARKER_KEY, JsonPrimitive(filledByApp))
      if (role != kept.role) put(ROLE_KEY, JsonPrimitive(role.fileValue))
      if (placement != kept.placement) putAll(placement.fileFields())
    }
    return JsonObject(prompt + changes)
  }

  companion object {
    fun of(entry: PresetEntry) = PromptFields(entry.name, entry.text, entry.filledByApp, entry.role, entry.placement)

    fun of(prompt: JsonObject, identifier: String) = PromptFields(
      name = prompt.string(NAME_KEY) ?: identifier,
      text = prompt.string(CONTENT_KEY).orEmpty(),
      filledByApp = prompt.quotedOrBareBoolean(MARKER_KEY) == true,
      role = roleOf(prompt.string(ROLE_KEY)),
      placement = if (prompt.int(POSITION_KEY) == IN_CHAT_POSITION) {
        EntryPlacement.InChat((prompt.int(DEPTH_KEY) ?: PINNED_DEFAULT_DEPTH).coerceAtLeast(0))
      } else {
        EntryPlacement.Relative
      },
    )
  }
}

private fun EntryPlacement.fileFields(): Map<String, JsonPrimitive> = when (this) {
  EntryPlacement.Relative -> mapOf(POSITION_KEY to JsonPrimitive(RELATIVE_POSITION))
  is EntryPlacement.InChat -> mapOf(POSITION_KEY to JsonPrimitive(IN_CHAT_POSITION), DEPTH_KEY to JsonPrimitive(depth))
}

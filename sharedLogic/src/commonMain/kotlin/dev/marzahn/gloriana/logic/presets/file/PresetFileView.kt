package dev.marzahn.gloriana.logic.presets.file

import dev.marzahn.gloriana.logic.json.arrayAt
import dev.marzahn.gloriana.logic.json.boolean
import dev.marzahn.gloriana.logic.json.int
import dev.marzahn.gloriana.logic.json.objectAt
import dev.marzahn.gloriana.logic.json.string
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class FileEntry(val identifier: String, val fields: PromptFields, val enabled: Boolean)

internal data class FileGroup(val name: String, val at: Int)

internal class GlobalOrder(val index: Int, val list: JsonObject, val items: List<JsonElement>)

internal class OrderSlot(val index: Int, val identifier: String, val item: JsonObject)

internal class PresetFileView(val source: JsonObject) {
  val prompts: List<JsonElement> = source.arrayAt(PROMPTS_KEY)

  val firstPrompts: Map<String, IndexedValue<JsonObject>> = prompts.withIndex()
    .mapNotNull { (index, element) ->
      (element as? JsonObject)?.let { prompt ->
        prompt.string(IDENTIFIER_KEY)?.let { it to IndexedValue(index, prompt) }
      }
    }
    .distinctBy { (identifier) -> identifier }
    .toMap()

  val globalOrder: GlobalOrder? = source.arrayAt(PROMPT_ORDER_KEY).withIndex()
    .mapNotNull { (index, element) ->
      (element as? JsonObject)?.let { list -> (list[ORDER_KEY] as? JsonArray)?.let { GlobalOrder(index, list, it) } }
    }
    .let { lists ->
      lists.firstOrNull { it.list.int(CHARACTER_ID_KEY) == GLOBAL_ORDER_CHARACTER_ID } ?: lists.firstOrNull()
    }

  val entrySlots: List<OrderSlot> = globalOrder?.items.orEmpty().withIndex()
    .mapNotNull { (index, element) ->
      (element as? JsonObject)?.let { item ->
        item.string(IDENTIFIER_KEY)?.takeIf { it in firstPrompts }?.let { OrderSlot(index, it, item) }
      }
    }
    .distinctBy { it.identifier }

  val entries: List<FileEntry> = if (globalOrder == null) {
    firstPrompts.keys.map { entry(it, enabled = true) }
  } else {
    entrySlots.map { entry(it.identifier, enabled = it.item.boolean(ENABLED_KEY) ?: true) }
  }

  val gloriana: JsonObject? = source.objectAt(GLORIANA_KEY)

  val groups: List<FileGroup> = gloriana?.arrayAt(GROUPS_KEY).orEmpty()
    .mapNotNull { element ->
      (element as? JsonObject)?.let { group ->
        val name = group.string(GROUP_NAME_KEY)
        val at = group.int(GROUP_AT_KEY)
        if (name != null && at != null && at in 0..entries.size) FileGroup(name, at) else null
      }
    }
    .sortedBy { it.at }

  val wrappers: Map<WrapperPrompt, String> = WrapperPrompt.entries.associateWith { prompt ->
    holder(prompt.home)?.string(prompt.key) ?: prompt.pinnedDefault
  }

  private fun holder(home: WrapperHome): JsonObject? = when (home) {
    WrapperHome.TopLevel -> source
    WrapperHome.GlorianaKey -> gloriana
  }

  private fun entry(identifier: String, enabled: Boolean) =
    FileEntry(identifier, PromptFields.of(firstPrompts.getValue(identifier).value, identifier), enabled)
}

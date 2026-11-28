package dev.marzahn.gloriana.logic.presets.file

import dev.marzahn.gloriana.logic.json.arrayAt
import dev.marzahn.gloriana.logic.json.orNone
import dev.marzahn.gloriana.logic.json.quotedOrBareBoolean
import dev.marzahn.gloriana.logic.json.string
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
private val PresetFileJson = Json {
  prettyPrint = true
  prettyPrintIndent = "    "
}

private sealed interface KeyWrite {
  data class Put(val value: JsonElement) : KeyWrite

  data object Remove : KeyWrite
}

private fun JsonObject.applying(writes: Map<String, KeyWrite>): JsonObject {
  val puts = writes.mapNotNull { (key, write) ->
    when (write) {
      is KeyWrite.Put -> key to write.value
      KeyWrite.Remove -> null
    }
  }
  val removed = writes.filterValues { it == KeyWrite.Remove }.keys
  return JsonObject(this + puts - removed)
}

internal object PresetJsonWriter {
  fun encode(content: PresetContent, source: JsonObject): ByteArray =
    PresetFileJson.encodeToString(JsonObject.serializer(), write(content, source)).encodeToByteArray()

  fun write(content: PresetContent, source: JsonObject): JsonObject {
    val view = PresetFileView(source)
    val writes = buildMap {
      put(PROMPTS_KEY, KeyWrite.Put(prompts(content.entries, view)))
      promptOrder(content.entries, view)?.let { put(PROMPT_ORDER_KEY, KeyWrite.Put(it)) }
      putAll(wrapperWrites(content, view, WrapperHome.TopLevel))
      gloriana(content, view)?.let { put(GLORIANA_KEY, it) }
    }
    return source.applying(writes)
  }

  private fun prompts(entries: List<PresetEntry>, view: PresetFileView): JsonArray {
    val entriesByIdentifier = entries.associateBy { it.identifier }
    return JsonArray(view.prompts.mapIndexed { index, element ->
      val prompt = element as? JsonObject
      val identifier = prompt?.string(IDENTIFIER_KEY)
      val entry = identifier?.takeIf { view.firstPrompts[it]?.index == index }?.let(entriesByIdentifier::get)
      if (prompt == null || entry == null) element else PromptFields.of(entry).overlaidOn(prompt, entry.identifier)
    })
  }

  private fun promptOrder(entries: List<PresetEntry>, view: PresetFileView): JsonArray? {
    if (entries.map { it.identifier to it.enabled } == view.entries.map { it.identifier to it.enabled }) return null
    val keptItems = view.entrySlots.associate { it.identifier to it.item }
    val items = entries.map { entry -> orderItem(entry, keptItems[entry.identifier]) }
    val lists = view.source.arrayAt(PROMPT_ORDER_KEY)
    return when (val global = view.globalOrder) {
      null -> JsonArray(
        lists + JsonObject(
          mapOf(CHARACTER_ID_KEY to JsonPrimitive(GLOBAL_ORDER_CHARACTER_ID), ORDER_KEY to JsonArray(items)),
        ),
      )
      else -> {
        val known = view.entries.map { it.identifier }.toSet() + entries.map { it.identifier }
        val rewritten = global.rewritten(view.entrySlots.map { it.index }.toSet(), items, known)
        JsonArray(lists.mapIndexed { index, list -> if (index == global.index) rewritten else list })
      }
    }
  }

  private fun GlobalOrder.rewritten(slots: Set<Int>, entryItems: List<JsonObject>, known: Set<String>): JsonObject {
    val pending = entryItems.iterator()
    val inPlace = items.withIndex().mapNotNull { (index, item) ->
      when {
        index in slots -> if (pending.hasNext()) pending.next() else null
        (item as? JsonObject)?.string(IDENTIFIER_KEY) in known -> null
        else -> item
      }
    }
    return JsonObject(list + (ORDER_KEY to JsonArray(inPlace + pending.asSequence())))
  }

  private fun orderItem(entry: PresetEntry, kept: JsonObject?): JsonObject = when {
    kept == null -> JsonObject(
      mapOf(IDENTIFIER_KEY to JsonPrimitive(entry.identifier), ENABLED_KEY to JsonPrimitive(entry.enabled)),
    )
    (kept.quotedOrBareBoolean(ENABLED_KEY) ?: true) == entry.enabled -> kept
    else -> JsonObject(kept + (ENABLED_KEY to JsonPrimitive(entry.enabled)))
  }

  private fun wrapperWrites(content: PresetContent, view: PresetFileView, home: WrapperHome): Map<String, KeyWrite> =
    WrapperPrompt.entries
      .filter { it.home == home && content.wrappers[it] != view.wrappers[it] }
      .associate { prompt ->
        val text = content.wrappers[prompt]
        val omitted = home.omitsDefault && text == prompt.shippedDefault
        prompt.key to if (omitted) KeyWrite.Remove else KeyWrite.Put(JsonPrimitive(text))
      }

  private fun gloriana(content: PresetContent, view: PresetFileView): KeyWrite? {
    val groups = groups(content)
    val writes = wrapperWrites(content, view, WrapperHome.GlorianaKey).toMutableMap()
    if (groups != view.groups) {
      writes[GROUPS_KEY] = if (groups.isEmpty()) KeyWrite.Remove else KeyWrite.Put(JsonArray(groups.map(::groupJson)))
    }
    if (writes.isEmpty()) return null
    val written = view.gloriana.orNone().applying(writes)
    return if (written.isEmpty()) KeyWrite.Remove else KeyWrite.Put(written)
  }

  private fun groups(content: PresetContent): List<FileGroup> {
    val entriesAbove = content.rows.runningFold(0) { count, row -> if (row is PresetEntry) count + 1 else count }
    return content.rows.zip(entriesAbove)
      .mapNotNull { (row, above) ->
        when (row) {
          is PresetGroup -> FileGroup(row.name, above)
          is PresetEntry -> null
        }
      }
  }

  private fun groupJson(group: FileGroup) =
    JsonObject(mapOf(GROUP_NAME_KEY to JsonPrimitive(group.name), GROUP_AT_KEY to JsonPrimitive(group.at)))
}

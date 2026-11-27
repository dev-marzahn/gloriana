package dev.marzahn.gloriana.logic.characters

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private val TEXT_KEYS = listOf(
  "name", "description", "personality", "scenario", "first_mes", "mes_example", "nickname", "creator",
  "character_version", "creator_notes", "system_prompt", "post_history_instructions",
)
private val CCV1_KEYS = TEXT_KEYS.take(6)
private val LIST_KEYS = listOf("alternate_greetings", "group_only_greetings")
private val DEFAULTS_A_WRITER_MAY_ADD = (TEXT_KEYS + LIST_KEYS + listOf("tags", "extensions", "assets")).toSet()
private const val ASSET_FILE_URI = "__asset:"
private val FILE_URIS = listOf("embeded://", ASSET_FILE_URI)

enum class Container { Png, Json }

object CardDiff {
  fun differences(
    source: JsonObject,
    written: JsonObject,
    sourceHoldsFiles: Boolean,
    container: Container,
  ): List<String> {
    val sourceData = source["data"] as? JsonObject
    val hasData = source["spec"].let { it != null && it != JsonNull } && sourceData?.get("name") is JsonPrimitive
    val sourceFields = if (hasData) checkNotNull(sourceData) else JsonObject(source.filterKeys { it in CCV1_KEYS })
    val data = written.getValue("data").jsonObject
    return buildList {
      if (written["spec"] != JsonPrimitive("chara_card_v3")) add("spec is ${written["spec"]}")
      if (written["spec_version"] != JsonPrimitive("3.0")) add("spec_version is ${written["spec_version"]}")
      addAll(fieldDifferences(sourceFields, data))
      addAll(topLevelDifferences(source, written, data))
      addAll(extensionDifferences(sourceFields, data))
      addAll(assetDifferences(sourceFields, data, sourceHoldsFiles, container))
    }
  }

  private fun fieldDifferences(source: JsonObject, data: JsonObject): List<String> = buildList {
    source.filterKeys { it != "extensions" && it != "assets" }.forEach { (key, value) ->
      val expected = when (key) {
        in TEXT_KEYS -> (value as? JsonPrimitive)?.takeIf { it.isString } ?: JsonPrimitive("")
        in LIST_KEYS -> JsonArray((value as? JsonArray).orEmpty().filter { it is JsonPrimitive && it.isString })
        else -> value
      }
      if (data[key] != expected) add("data.$key is ${data[key]}, expected $expected")
    }
    data.keys.filter { it !in source && it !in DEFAULTS_A_WRITER_MAY_ADD }.forEach { add("data.$it was added") }
    TEXT_KEYS.filter { it !in source && data[it] != JsonPrimitive("") }.forEach { add("data.$it is not empty") }
  }

  private fun topLevelDifferences(source: JsonObject, written: JsonObject, data: JsonObject): List<String> = buildList {
    source.filterKeys { it !in setOf("spec", "spec_version", "data") && it !in CCV1_KEYS }.forEach { (key, value) ->
      if (written[key] != value) add("$key is ${written[key]}, expected $value")
    }
    CCV1_KEYS.filter { written[it] != data[it] }.forEach { add("top-level $it is not data.$it") }
  }

  private fun extensionDifferences(source: JsonObject, data: JsonObject): List<String> {
    val expected = (source["extensions"] as? JsonObject).orEmpty()
    val written = data["extensions"]?.jsonObject.orEmpty()
    return if (written != expected) listOf("extensions are $written, expected $expected") else emptyList()
  }

  private fun assetDifferences(
    source: JsonObject,
    data: JsonObject,
    sourceHoldsFiles: Boolean,
    container: Container,
  ): List<String> {
    val sourceAssets = (source["assets"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    val expected = sourceAssets.filterNot { asset ->
      val uri = (asset["uri"] as? JsonPrimitive)?.content.orEmpty()
      FILE_URIS.any(uri::startsWith) || (sourceHoldsFiles && asset.isDefaultIcon())
    }
    val written = data["assets"]?.jsonArray.orEmpty().filterIsInstance<JsonObject>()
    val writtenWithoutFiles = when (container) {
      Container.Json -> written
      Container.Png -> written.drop(1)
        .filterNot { (it["uri"] as? JsonPrimitive)?.content.orEmpty().startsWith(ASSET_FILE_URI) }
    }
    return buildList {
      val avatarFirst = written.firstOrNull()?.isDefaultIcon() == true
      if (container == Container.Png && !avatarFirst) add("the PNG names no avatar first")
      if (writtenWithoutFiles != expected) add("assets are $writtenWithoutFiles, expected $expected")
    }
  }

  private fun JsonObject.isDefaultIcon() =
    this["type"] == JsonPrimitive("icon") && this["uri"] == JsonPrimitive("ccdefault:")

  private fun Map<String, JsonElement>?.orEmpty(): JsonObject = JsonObject(this ?: emptyMap())
}

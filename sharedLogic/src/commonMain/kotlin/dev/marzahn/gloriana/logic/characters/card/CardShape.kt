package dev.marzahn.gloriana.logic.characters.card

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val SPEC_KEY = "spec"
private const val DATA_KEY = "data"

internal sealed interface CardShape {
  val root: JsonObject
  val fields: JsonObject

  class Ccv1(override val root: JsonObject) : CardShape {
    override val fields: JsonObject = root
  }

  class WithData(override val root: JsonObject, override val fields: JsonObject) : CardShape

  companion object {
    fun of(root: JsonObject): CardShape {
      val data = root[DATA_KEY] as? JsonObject
      val hasSpec = root[SPEC_KEY].let { it != null && it != JsonNull }
      return if (hasSpec && data?.string(CardTextField.Name.key) != null) WithData(root, data) else Ccv1(root)
    }
  }
}

internal fun JsonObject.hasKnownSpec() = string(SPEC_KEY) in setOf(SPEC_V2, SPEC_V3)

internal fun JsonObject.string(key: String): String? = this[key].asString()

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.strings(key: String): List<String> =
  (this[key] as? JsonArray).orEmpty().mapNotNull { it.asString() }

internal fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject?.orNone(): JsonObject = this ?: JsonObject(emptyMap())

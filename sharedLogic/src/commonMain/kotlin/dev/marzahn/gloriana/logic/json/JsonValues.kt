package dev.marzahn.gloriana.logic.json

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.string(key: String): String? = this[key].asString()

internal fun JsonObject.strings(key: String): List<String> = arrayAt(key).mapNotNull { it.asString() }

internal fun JsonObject.long(key: String): Long? = number(key)?.longOrNull

internal fun JsonObject.int(key: String): Int? = number(key)?.intOrNull

internal fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.number(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }

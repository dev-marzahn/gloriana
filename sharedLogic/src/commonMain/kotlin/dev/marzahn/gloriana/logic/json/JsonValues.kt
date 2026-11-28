package dev.marzahn.gloriana.logic.json

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonElement?.asLiteral(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it.isString }

internal fun JsonElement?.asBoolean(): Boolean? = asLiteral()?.booleanOrNull

internal fun JsonObject.string(key: String): String? = this[key].asString()

internal fun JsonObject.strings(key: String): List<String> = arrayAt(key).mapNotNull { it.asString() }

internal fun JsonObject.long(key: String): Long? = this[key].asLiteral()?.longOrNull

internal fun JsonObject.int(key: String): Int? = this[key].asLiteral()?.intOrNull

internal fun JsonObject.quotedOrBareLong(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.quotedOrBareInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.quotedOrBareBoolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

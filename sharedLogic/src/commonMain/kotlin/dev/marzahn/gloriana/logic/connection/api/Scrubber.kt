package dev.marzahn.gloriana.logic.connection.api

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal const val MASK = "•••"

internal class Scrubber(secrets: Set<String>) {
  private val forms: List<String> = secrets
    .flatMap { listOf(it, jsonEscaped(it), jsonEscaped(it).replace("/", "\\/"), it.encodeURLParameter()) }
    .filter(String::isNotEmpty)
    .distinct()
    .sortedByDescending(String::length)

  fun scrub(text: String): String = forms.fold(text) { scrubbed, form -> scrubbed.replace(form, MASK) }
}

private fun jsonEscaped(value: String): String = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
  .removeSurrounding("\"")

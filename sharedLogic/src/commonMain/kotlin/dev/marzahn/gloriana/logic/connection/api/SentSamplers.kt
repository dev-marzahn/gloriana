package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

internal class SentSamplers(val values: Map<Sampler, Number>) {
  fun putInto(body: JsonObjectBuilder) {
    values.forEach { (sampler, value) -> body.put(sampler.wireName, JsonPrimitive(value)) }
  }
}

internal fun mergeSamplers(format: ApiFormat, global: Samplers, own: Samplers): SentSamplers =
  SentSamplers(
    format.samplerRanges.mapNotNull { (sampler, range) ->
      val value = sampler.valueIn(own) ?: sampler.valueIn(global)?.takeIf(range::holds)
      value?.let { sampler to it }
    }.toMap(),
  )

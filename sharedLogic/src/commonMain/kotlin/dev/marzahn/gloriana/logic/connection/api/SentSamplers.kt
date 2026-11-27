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

sealed interface SentValue {
  val value: Number?

  data class Own(override val value: Number) : SentValue

  data class Global(override val value: Number) : SentValue

  data object NotSet : SentValue {
    override val value: Number? = null
  }
}

internal fun sentValue(format: ApiFormat, sampler: Sampler, global: Samplers, own: Samplers): SentValue {
  val range = format.samplerRanges[sampler] ?: return SentValue.NotSet
  val ownValue = sampler.valueIn(own)
  val globalValue = sampler.valueIn(global)?.takeIf(range::holds)
  return when {
    ownValue != null -> SentValue.Own(ownValue)
    globalValue != null -> SentValue.Global(globalValue)
    else -> SentValue.NotSet
  }
}

internal fun mergeSamplers(format: ApiFormat, global: Samplers, own: Samplers): SentSamplers =
  SentSamplers(
    format.samplerRanges.keys.mapNotNull { sampler ->
      sentValue(format, sampler, global, own).value?.let { sampler to it }
    }.toMap(),
  )

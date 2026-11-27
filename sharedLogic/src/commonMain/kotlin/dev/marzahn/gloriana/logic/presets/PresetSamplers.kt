package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.SamplerKind
import dev.marzahn.gloriana.logic.connection.Samplers
import kotlin.math.floor

sealed interface PresetSampler {
  data class OnConnection(val sampler: Sampler) : PresetSampler

  enum class WithoutConnectionField : PresetSampler {
    TopA,
    MinP,
    RepetitionPenalty,
  }
}

data class PresetSamplers(val values: Map<PresetSampler, Double>) {
  val isEmpty: Boolean get() = values.isEmpty()
}

private val longRange = Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()

internal fun PresetSampler.accepts(value: Double): Boolean = value.isFinite() && when (this) {
  is PresetSampler.OnConnection -> sampler.accepts(value)
  is PresetSampler.WithoutConnectionField -> true
}

private fun Sampler.accepts(value: Double): Boolean {
  val fitsKind = kind == SamplerKind.Decimal || (value == floor(value) && value in longRange)
  return fitsKind && when (this) {
    Sampler.TopK -> value > 0
    Sampler.Seed -> value >= 0
    else -> true
  }
}

internal fun ApiFormat.sends(sampler: Sampler, value: Double): Boolean {
  val range = samplerRanges[sampler]
  return range != null && value.isFinite() && sampler.accepts(value) && range.holds(sampler.typed(value))
}

internal fun Sampler.written(samplers: Samplers, value: Number): Samplers = when (this) {
  Sampler.Temperature -> samplers.copy(temperature = value.toDouble())
  Sampler.TopP -> samplers.copy(topP = value.toDouble())
  Sampler.TopK -> samplers.copy(topK = value.toLong())
  Sampler.FrequencyPenalty -> samplers.copy(frequencyPenalty = value.toDouble())
  Sampler.PresencePenalty -> samplers.copy(presencePenalty = value.toDouble())
  Sampler.Seed -> samplers.copy(seed = value.toLong())
}

internal fun Sampler.typed(value: Double): Number = when (kind) {
  SamplerKind.Decimal -> value
  SamplerKind.Whole -> value.toLong()
}

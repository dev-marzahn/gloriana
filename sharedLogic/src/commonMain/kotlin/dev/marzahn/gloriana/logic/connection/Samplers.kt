package dev.marzahn.gloriana.logic.connection

import kotlinx.serialization.Serializable

enum class DecimalSampler {
  Temperature,
  TopP,
  FrequencyPenalty,
  PresencePenalty,
}

@Serializable
data class Samplers(
  val temperature: Double? = null,
  val topP: Double? = null,
  val topK: Long? = null,
  val frequencyPenalty: Double? = null,
  val presencePenalty: Double? = null,
  val seed: Long? = null,
) {
  fun notFinite(): Set<DecimalSampler> = mapOf(
    DecimalSampler.Temperature to temperature,
    DecimalSampler.TopP to topP,
    DecimalSampler.FrequencyPenalty to frequencyPenalty,
    DecimalSampler.PresencePenalty to presencePenalty,
  ).filterValues { it != null && !it.isFinite() }.keys
}

package dev.marzahn.gloriana.logic.connection

import kotlinx.serialization.Serializable

@Serializable
data class Samplers(
  val temperature: Double? = null,
  val topP: Double? = null,
  val topK: Long? = null,
  val frequencyPenalty: Double? = null,
  val presencePenalty: Double? = null,
  val seed: Long? = null,
)

enum class SamplerKind {
  Decimal,
  Whole,
  ;

  fun isNonFinite(value: Number): Boolean = this == Decimal && !value.toDouble().isFinite()
}

enum class Sampler(val wireName: String, val kind: SamplerKind, val valueIn: (Samplers) -> Number?) {
  Temperature("temperature", SamplerKind.Decimal, Samplers::temperature),
  TopP("top_p", SamplerKind.Decimal, Samplers::topP),
  TopK("top_k", SamplerKind.Whole, Samplers::topK),
  FrequencyPenalty("frequency_penalty", SamplerKind.Decimal, Samplers::frequencyPenalty),
  PresencePenalty("presence_penalty", SamplerKind.Decimal, Samplers::presencePenalty),
  Seed("seed", SamplerKind.Whole, Samplers::seed),
}

sealed interface SamplerRange {
  fun holds(value: Number): Boolean

  data class Decimal(val range: ClosedFloatingPointRange<Double>) : SamplerRange {
    override fun holds(value: Number) = value is Double && value in range
  }

  data class Whole(val range: LongRange) : SamplerRange {
    override fun holds(value: Number) = value is Long && value in range
  }
}

fun Samplers.with(sampler: Sampler, value: Number?): Samplers = when (sampler) {
  Sampler.Temperature -> copy(temperature = value?.toDouble())
  Sampler.TopP -> copy(topP = value?.toDouble())
  Sampler.TopK -> copy(topK = value?.toLong())
  Sampler.FrequencyPenalty -> copy(frequencyPenalty = value?.toDouble())
  Sampler.PresencePenalty -> copy(presencePenalty = value?.toDouble())
  Sampler.Seed -> copy(seed = value?.toLong())
}

fun Samplers.setValues(): List<Pair<Sampler, Number>> =
  Sampler.entries.mapNotNull { sampler -> sampler.valueIn(this)?.let { sampler to it } }

private fun Sampler.within(least: Number, most: Number): Pair<Sampler, SamplerRange> = this to when (kind) {
  SamplerKind.Decimal -> SamplerRange.Decimal(least.toDouble()..most.toDouble())
  SamplerKind.Whole -> SamplerRange.Whole(least.toLong()..most.toLong())
}

private const val UNIT = 1.0
private const val OPENAI_TEMPERATURE = 2.0
private const val OPENAI_PENALTY = 2.0

internal val anthropicSamplerRanges: Map<Sampler, SamplerRange> = mapOf(
  Sampler.Temperature.within(0.0, UNIT),
  Sampler.TopP.within(0.0, UNIT),
  Sampler.TopK.within(0L, Long.MAX_VALUE),
)

internal val chatCompletionsSamplerRanges: Map<Sampler, SamplerRange> = mapOf(
  Sampler.Temperature.within(0.0, OPENAI_TEMPERATURE),
  Sampler.TopP.within(0.0, UNIT),
  Sampler.FrequencyPenalty.within(-OPENAI_PENALTY, OPENAI_PENALTY),
  Sampler.PresencePenalty.within(-OPENAI_PENALTY, OPENAI_PENALTY),
  Sampler.Seed.within(Long.MIN_VALUE, Long.MAX_VALUE),
)

internal val responsesSamplerRanges: Map<Sampler, SamplerRange> = mapOf(
  Sampler.Temperature.within(0.0, OPENAI_TEMPERATURE),
  Sampler.TopP.within(0.0, UNIT),
)

internal fun Samplers.outsideEveryFormat(): Set<Sampler> = setValues()
  .filter { (sampler, value) -> ApiFormat.entries.none { it.samplerRanges[sampler]?.holds(value) == true } }
  .mapTo(mutableSetOf()) { (sampler) -> sampler }

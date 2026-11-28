package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.SamplerKind
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.setValues
import dev.marzahn.gloriana.logic.connection.with
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.floor

sealed interface PresetSampler {
  data class OnConnection(val sampler: Sampler) : PresetSampler

  enum class WithoutConnectionField : PresetSampler {
    TopA,
    MinP,
    RepetitionPenalty,
  }
}

@ConsistentCopyVisibility
data class PresetSamplers private constructor(
  val onConnection: Samplers,
  val withoutConnectionField: Map<PresetSampler.WithoutConnectionField, Double>,
) {
  val isEmpty: Boolean get() = onConnection == Samplers() && withoutConnectionField.isEmpty()

  companion object {
    fun of(
      onConnection: Samplers = Samplers(),
      withoutConnectionField: Map<PresetSampler.WithoutConnectionField, Double> = emptyMap(),
    ): PresetSamplers = PresetSamplers(
      onConnection.setValues()
        .filter { (sampler, value) -> sampler.setsAValue(value) }
        .fold(Samplers()) { samplers, (sampler, value) -> samplers.with(sampler, value) },
      withoutConnectionField.filterValues(Double::isFinite),
    )

    internal fun read(fileValue: (PresetSampler) -> JsonPrimitive?): PresetSamplers = of(
      onConnection = Sampler.entries.fold(Samplers()) { samplers, sampler ->
        samplers.with(sampler, fileValue(PresetSampler.OnConnection(sampler))?.let(sampler::fileNumber))
      },
      withoutConnectionField = PresetSampler.WithoutConnectionField.entries.mapNotNull { sampler ->
        fileValue(sampler)?.finiteNumber()?.let { sampler to it }
      }.toMap(),
    )
  }
}

private fun Sampler.setsAValue(value: Number): Boolean = when (this) {
  Sampler.TopK -> value.toLong() > 0
  Sampler.Seed -> value.toLong() >= 0
  Sampler.Temperature, Sampler.TopP, Sampler.FrequencyPenalty, Sampler.PresencePenalty -> true
}

private fun Sampler.fileNumber(primitive: JsonPrimitive): Number? = when (kind) {
  SamplerKind.Decimal -> primitive.finiteNumber()
  SamplerKind.Whole -> primitive.wholeNumber()
}

private fun JsonPrimitive.finiteNumber(): Double? = takeUnless { it.isString }?.doubleOrNull?.takeIf(Double::isFinite)

private fun JsonPrimitive.wholeNumber(): Long? =
  takeUnless { it.isString }?.let { number -> number.longOrNull ?: number.doubleOrNull?.wholeLongOrNull() }

private val FIRST_DOUBLE_BEYOND_LONG = -Long.MIN_VALUE.toDouble()

private fun Double.wholeLongOrNull(): Long? =
  takeIf { it == floor(it) && it >= -FIRST_DOUBLE_BEYOND_LONG && it < FIRST_DOUBLE_BEYOND_LONG }?.toLong()

package dev.marzahn.gloriana.ui.connections

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.SamplerKind
import dev.marzahn.gloriana.logic.connection.SamplerRange
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.with
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.error_sampler_at_least
import dev.marzahn.gloriana.ui.resources.error_sampler_no_format
import dev.marzahn.gloriana.ui.resources.error_sampler_number
import dev.marzahn.gloriana.ui.resources.error_sampler_range
import dev.marzahn.gloriana.ui.resources.error_sampler_whole
import dev.marzahn.gloriana.ui.resources.range_at_least
import dev.marzahn.gloriana.ui.resources.range_between
import dev.marzahn.gloriana.ui.resources.range_whole
import dev.marzahn.gloriana.ui.resources.sampler_frequency_penalty
import dev.marzahn.gloriana.ui.resources.sampler_presence_penalty
import dev.marzahn.gloriana.ui.resources.sampler_seed
import dev.marzahn.gloriana.ui.resources.sampler_temperature
import dev.marzahn.gloriana.ui.resources.sampler_top_k
import dev.marzahn.gloriana.ui.resources.sampler_top_p
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.floor

private const val LARGEST_EXACT_WHOLE = 1e15

sealed interface SamplerEntry {
  data object Unset : SamplerEntry

  data class Set(val value: Number) : SamplerEntry

  data object NotANumber : SamplerEntry

  data object NotWhole : SamplerEntry
}

sealed interface SamplerError {
  data object NotANumber : SamplerError

  data object NotWhole : SamplerError

  data class OutOfRange(val range: SamplerRange) : SamplerError

  data class NoFormatTakes(val value: Number, val widest: SamplerRange) : SamplerError
}

fun Sampler.entry(text: String): SamplerEntry {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return SamplerEntry.Unset
  return when (kind) {
    SamplerKind.Decimal -> trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(SamplerEntry::Set)
      ?: SamplerEntry.NotANumber
    SamplerKind.Whole -> trimmed.toLongOrNull()?.let(SamplerEntry::Set)
      ?: if (trimmed.toDoubleOrNull() != null) SamplerEntry.NotWhole else SamplerEntry.NotANumber
  }
}

val SamplerEntry.error: SamplerError?
  get() = when (this) {
    SamplerEntry.NotANumber -> SamplerError.NotANumber
    SamplerEntry.NotWhole -> SamplerError.NotWhole
    SamplerEntry.Unset, is SamplerEntry.Set -> null
  }

data class SamplerTexts(val texts: Map<Sampler, String>) : ComparesByValue<SamplerTexts> {
  operator fun get(sampler: Sampler): String = texts[sampler].orEmpty()

  fun with(sampler: Sampler, text: String) = SamplerTexts(texts + (sampler to text))

  fun entryErrors(only: Set<Sampler> = Sampler.entries.toSet()): Map<Sampler, SamplerError> =
    only.mapNotNull { sampler -> sampler.entry(this[sampler]).error?.let { sampler to it } }.toMap()

  fun toSamplers(only: Set<Sampler> = Sampler.entries.toSet()): Samplers =
    only.fold(Samplers()) { samplers, sampler ->
      samplers.with(sampler, (sampler.entry(this[sampler]) as? SamplerEntry.Set)?.value)
    }

  fun isSet(sampler: Sampler): Boolean = this[sampler].isNotBlank()

  fun holdsValue(sampler: Sampler): Boolean = sampler.entry(this[sampler]) is SamplerEntry.Set

  override fun sameAs(other: SamplerTexts): Boolean = byValue() == other.byValue()

  private fun byValue(): Map<Sampler, String> = Sampler.entries.associateWith { sampler ->
    (sampler.entry(this[sampler]) as? SamplerEntry.Set)?.value?.shown() ?: this[sampler].trim()
  }

  companion object {
    fun of(samplers: Samplers) =
      SamplerTexts(Sampler.entries.associateWith { sampler -> sampler.valueIn(samplers)?.shown().orEmpty() })
  }
}

fun Number.shown(): String {
  if (this is Long) return toString()
  val value = toDouble()
  return if (value == floor(value) && abs(value) < LARGEST_EXACT_WHOLE) value.toLong().toString() else value.toString()
}

val Sampler.label: StringResource
  get() = when (this) {
    Sampler.Temperature -> Res.string.sampler_temperature
    Sampler.TopP -> Res.string.sampler_top_p
    Sampler.TopK -> Res.string.sampler_top_k
    Sampler.FrequencyPenalty -> Res.string.sampler_frequency_penalty
    Sampler.PresencePenalty -> Res.string.sampler_presence_penalty
    Sampler.Seed -> Res.string.sampler_seed
  }

class FormatRange(val formats: List<ApiFormat>, val range: SamplerRange)

fun Sampler.rangesByFormat(): List<FormatRange> =
  ApiFormat.entries.mapNotNull { format -> format.samplerRanges[this]?.let { format to it } }
    .groupBy({ it.second }, { it.first })
    .map { (range, formats) -> FormatRange(formats, range) }

fun Sampler.widestRange(): SamplerRange? = rangesByFormat().maxByOrNull { it.range.span }?.range

private val SamplerRange.span: Double
  get() = when (this) {
    is SamplerRange.Decimal -> range.endInclusive - range.start
    is SamplerRange.Whole -> range.last.toDouble() - range.first.toDouble()
  }

private sealed interface RangeShape {
  class Between(val least: String, val most: String) : RangeShape

  class AtLeast(val least: String) : RangeShape

  data object AnyWhole : RangeShape
}

private val SamplerRange.shape: RangeShape
  get() = when (this) {
    is SamplerRange.Decimal -> RangeShape.Between(range.start.shown(), range.endInclusive.shown())
    is SamplerRange.Whole -> when {
      range.first == Long.MIN_VALUE && range.last == Long.MAX_VALUE -> RangeShape.AnyWhole
      range.last == Long.MAX_VALUE -> RangeShape.AtLeast(range.first.shown())
      else -> RangeShape.Between(range.first.shown(), range.last.shown())
    }
  }

@Composable
fun rangeText(range: SamplerRange): String = when (val shape = range.shape) {
  is RangeShape.Between -> stringResource(Res.string.range_between, shape.least, shape.most)
  is RangeShape.AtLeast -> stringResource(Res.string.range_at_least, shape.least)
  RangeShape.AnyWhole -> stringResource(Res.string.range_whole)
}

@Composable
fun SamplerError.text(): String = when (this) {
  SamplerError.NotANumber -> stringResource(Res.string.error_sampler_number)
  SamplerError.NotWhole -> stringResource(Res.string.error_sampler_whole)
  is SamplerError.OutOfRange -> when (val shape = range.shape) {
    is RangeShape.Between -> stringResource(Res.string.error_sampler_range, shape.least, shape.most)
    is RangeShape.AtLeast -> stringResource(Res.string.error_sampler_at_least, shape.least)
    RangeShape.AnyWhole -> stringResource(Res.string.error_sampler_whole)
  }
  is SamplerError.NoFormatTakes -> stringResource(Res.string.error_sampler_no_format, value.shown(), rangeText(widest))
}

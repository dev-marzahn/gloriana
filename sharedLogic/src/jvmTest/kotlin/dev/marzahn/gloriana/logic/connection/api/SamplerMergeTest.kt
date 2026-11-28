package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.SamplerRange
import dev.marzahn.gloriana.logic.connection.Samplers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamplerMergeTest {
  private fun merged(format: ApiFormat, global: Samplers, own: Samplers = Samplers()) =
    mergeSamplers(format, global, own).values

  @Test
  fun theConnectionsValueWinsOverTheGlobalOne() {
    val global = Samplers(temperature = 0.4, topP = 0.8)

    val merged = merged(ApiFormat.OpenAiChatCompletions, global, own = Samplers(temperature = 1.3))

    assertEquals(mapOf(Sampler.Temperature to 1.3, Sampler.TopP to 0.8), merged)
  }

  @Test
  fun aGlobalValueReachesOnlyAFormatThatShowsItsSampler() {
    val global = Samplers(temperature = 0.5, topK = 40, frequencyPenalty = 0.2, seed = 7)

    assertEquals(mapOf(Sampler.Temperature to 0.5, Sampler.TopK to 40L), merged(ApiFormat.AnthropicMessages, global))
    assertEquals(
      mapOf(Sampler.Temperature to 0.5, Sampler.FrequencyPenalty to 0.2, Sampler.Seed to 7L),
      merged(ApiFormat.OpenAiChatCompletions, global),
    )
    assertEquals(mapOf<Sampler, Number>(Sampler.Temperature to 0.5), merged(ApiFormat.OpenAiResponses, global))
  }

  @Test
  fun aGlobalValueOutsideTheFormatsRangeIsNotSent() {
    val global = Samplers(temperature = 1.5, presencePenalty = -2.5)

    assertEquals(emptyMap(), merged(ApiFormat.AnthropicMessages, global))
    assertEquals(mapOf<Sampler, Number>(Sampler.Temperature to 1.5), merged(ApiFormat.OpenAiChatCompletions, global))
  }

  @Test
  fun theConnectionsOwnSamplerIsSentOnlyWhereItsFormatShowsIt() {
    val own = Samplers(topK = 20, seed = 3)

    assertEquals(mapOf<Sampler, Number>(Sampler.TopK to 20L), merged(ApiFormat.AnthropicMessages, Samplers(), own))
    assertEquals(mapOf<Sampler, Number>(Sampler.Seed to 3L), merged(ApiFormat.OpenAiChatCompletions, Samplers(), own))
  }

  @Test
  fun eachFormatsRangesAreTheOnesItsApiDocuments() {
    val unit = SamplerRange.Decimal(0.0..1.0)
    val openAiTemperature = SamplerRange.Decimal(0.0..2.0)
    val penalty = SamplerRange.Decimal(-2.0..2.0)

    assertEquals(
      mapOf(Sampler.Temperature to unit, Sampler.TopP to unit, Sampler.TopK to SamplerRange.Whole(0..Long.MAX_VALUE)),
      ApiFormat.AnthropicMessages.samplerRanges,
    )
    assertEquals(
      mapOf(
        Sampler.Temperature to openAiTemperature,
        Sampler.TopP to unit,
        Sampler.FrequencyPenalty to penalty,
        Sampler.PresencePenalty to penalty,
        Sampler.Seed to SamplerRange.Whole(Long.MIN_VALUE..Long.MAX_VALUE),
      ),
      ApiFormat.OpenAiChatCompletions.samplerRanges,
    )
    assertEquals(
      mapOf(Sampler.Temperature to openAiTemperature, Sampler.TopP to unit),
      ApiFormat.OpenAiResponses.samplerRanges,
    )
  }

  @Test
  fun onlyADecimalSamplerCanBeNonFinite() {
    assertTrue(Sampler.Temperature.kind.isNonFinite(Double.NaN))
    assertFalse(Sampler.Temperature.kind.isNonFinite(0.5))
    assertFalse(Sampler.Seed.kind.isNonFinite(Long.MAX_VALUE))
  }

  @Test
  fun aWholeRangeHoldsOnlyWholeNumbers() {
    val topK = ApiFormat.AnthropicMessages.samplerRanges.getValue(Sampler.TopK)

    assertTrue(topK.holds(40L))
    assertFalse(topK.holds(40.5))
    assertFalse(topK.holds(-1L))
  }
}

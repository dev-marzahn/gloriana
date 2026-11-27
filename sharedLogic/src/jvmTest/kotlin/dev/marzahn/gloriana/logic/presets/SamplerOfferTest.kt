package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDeleteResult
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.SamplersUpdateResult
import dev.marzahn.gloriana.logic.connection.api.SentValue
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.connection.entered
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SamplerOfferTest {
  @TempDir
  lateinit var directory: Path

  private val fileSamplers = PresetSamplers(
    mapOf(
      fileSampler(Sampler.Temperature) to 0.85,
      fileSampler(Sampler.TopP) to 0.92,
      fileSampler(Sampler.TopK) to 40.0,
      fileSampler(Sampler.FrequencyPenalty) to 0.3,
      fileSampler(Sampler.Seed) to 7.0,
      PresetSampler.WithoutConnectionField.MinP to 0.05,
    ),
  )

  @Test
  fun theDifferenceListsWhatTheFormatSendsAndDiffersThenWhatItDoesnt() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(
        draft(key = KeyInput.Entered("sk-1"), samplers = Samplers(temperature = 0.7, frequencyPenalty = 0.3)),
      )
      library.global.setSamplers(Samplers(topP = 0.5))

      assertEquals(
        SamplerDifference(
          connectionId = connection.id,
          changes = listOf(
            SamplerChange(Sampler.Temperature, 0.85, SentValue.Own(0.7)),
            SamplerChange(Sampler.TopP, 0.92, SentValue.Global(0.5)),
            SamplerChange(Sampler.Seed, 7L, SentValue.NotSet),
          ),
          notCopied = listOf(
            NotCopied(fileSampler(Sampler.TopK), NotCopiedBecause.FormatSendsNone),
            NotCopied(PresetSampler.WithoutConnectionField.MinP, NotCopiedBecause.FormatSendsNone),
          ),
        ),
        found(library, fileSamplers, connection.id),
      )
    }
  }

  @Test
  fun aValueOutsideTheFormatsRangeIsNotCopiedAndAGlobalOneOutsideItIsNotSent() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = anthropic(library)
      library.global.setSamplers(Samplers(temperature = 1.5))
      val samplers = PresetSamplers(
        mapOf(fileSampler(Sampler.Temperature) to 1.5, fileSampler(Sampler.TopP) to 0.9),
      )

      val difference = found(library, samplers, connection.id)

      assertEquals(listOf(SamplerChange(Sampler.TopP, 0.9, SentValue.NotSet)), difference.changes)
      assertEquals(
        listOf(NotCopied(fileSampler(Sampler.Temperature), NotCopiedBecause.OutOfRange)),
        difference.notCopied,
      )
      val withinRange = PresetSamplers(mapOf(fileSampler(Sampler.Temperature) to 0.4))
      assertEquals(
        listOf(SamplerChange(Sampler.Temperature, 0.4, SentValue.NotSet)),
        found(library, withinRange, connection.id).changes,
      )
    }
  }

  @Test
  fun sentNowIsWhatTheMergeSendsEvenWhereThePresetRuleWouldReadItAsUnset() = runTest {
    PresetLibrary(directory).use { library ->
      val anthropic = anthropic(library)
      val chatCompletions = library.connections.created(draft(name = "Chat Completions"))
      library.global.setSamplers(Samplers(topK = 0, seed = -1))

      assertEquals(
        listOf(SamplerChange(Sampler.TopK, 40L, SentValue.Global(0L))),
        found(library, PresetSamplers(mapOf(fileSampler(Sampler.TopK) to 40.0)), anthropic.id).changes,
      )
      assertEquals(
        listOf(SamplerChange(Sampler.Seed, 7L, SentValue.Global(-1L))),
        found(library, PresetSamplers(mapOf(fileSampler(Sampler.Seed) to 7.0)), chatCompletions.id).changes,
      )
    }
  }

  @Test
  fun theOfferHoldsTheSameValidityRuleAsTheReader() = runTest {
    PresetLibrary(directory).use { library ->
      val samplers = listOf(-3.0, 0.0, 1e20).map { PresetSamplers(mapOf(fileSampler(Sampler.TopK) to it)) } +
        listOf(-1.0, 1e20).map { PresetSamplers(mapOf(fileSampler(Sampler.Seed) to it)) }
      val anthropic = anthropic(library)
      val chatCompletions = library.connections.created(draft(name = "Chat Completions"))

      samplers.forEach { offered ->
        val connection = if (fileSampler(Sampler.TopK) in offered.values) anthropic else chatCompletions
        val difference = found(library, offered, connection.id)
        assertEquals(emptyList(), difference.changes, "$offered")
        assertEquals(listOf(NotCopiedBecause.OutOfRange), difference.notCopied.map { it.because }, "$offered")
      }
    }
  }

  @Test
  fun anAnthropicConnectionIsOfferedTopKButNotThePenalties() = runTest {
    PresetLibrary(directory).use { library ->
      val difference = found(library, fileSamplers, anthropic(library).id)

      assertEquals(
        listOf(Sampler.Temperature, Sampler.TopP, Sampler.TopK),
        difference.changes.map { it.sampler },
      )
      assertEquals(
        listOf(
          fileSampler(Sampler.FrequencyPenalty),
          fileSampler(Sampler.Seed),
          PresetSampler.WithoutConnectionField.MinP,
        ),
        difference.notCopied.map { it.sampler },
      )
    }
  }

  @Test
  fun copyChangesExactlyTheAcceptedSamplersAndNothingElseOfTheConnection() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(
        draft(
          key = KeyInput.Entered("sk-1"),
          headers = listOf(HeaderDraft("X-Team", entered("MARKER-HEADER-1"))),
          samplers = Samplers(temperature = 0.7, presencePenalty = 0.4),
        ),
      )
      val difference = found(library, fileSamplers, connection.id)

      assertEquals(SamplersUpdateResult.Updated, library.offer.copy(connection.id, difference.changes))

      val copied = library.connections.stored(connection.id)
      assertEquals(
        connection.fields.copy(
          samplers = Samplers(temperature = 0.85, topP = 0.92, frequencyPenalty = 0.3, presencePenalty = 0.4, seed = 7),
        ),
        copied.fields,
      )
      assertEquals(connection.key?.bytes?.toList(), copied.key?.bytes?.toList())
      assertEquals(headerBytes(connection), headerBytes(copied))
      assertTrue(found(library, fileSamplers, connection.id).connectionMatches)
    }
  }

  @Test
  fun copyingSomeChangesLeavesTheOthersAndCopyingNoneWritesNothing() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(draft(samplers = Samplers(temperature = 0.7)))
      val difference = found(library, fileSamplers, connection.id)

      library.offer.copy(connection.id, difference.changes.filter { it.sampler == Sampler.Seed })
      assertEquals(Samplers(temperature = 0.7, seed = 7), library.connections.stored(connection.id).fields.samplers)

      assertEquals(SamplersUpdateResult.Unchanged, library.offer.copy(connection.id, emptyList()))
    }
  }

  @Test
  fun aDeletedConnectionHasNoDifferenceAndTakesNoCopy() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      val difference = found(library, fileSamplers, connection.id)
      assertEquals(ConnectionDeleteResult.Deleted, library.connections.repository.delete(connection.id))

      assertEquals(DifferenceResult.ConnectionGone, library.offer.difference(fileSamplers, connection.id))
      assertEquals(SamplersUpdateResult.Gone, library.offer.copy(connection.id, difference.changes))
    }
  }

  @Test
  fun aConnectionThisVersionCantReadIsToldApartFromAGoneOne() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      val dao = library.connections.database.connectionDao()
      dao.update(checkNotNull(dao.find(connection.id)).copy(format = "AFormatFromANewerVersion"))

      assertEquals(DifferenceResult.Unreadable, library.offer.difference(fileSamplers, connection.id))
      assertEquals(SamplersUpdateResult.Unreadable, library.offer.copy(connection.id, emptyList()))
    }
  }

  @Test
  fun aCopyAfterTheFormatChangedWritesOnlyWhatTheNewFormatSends() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      val difference = found(library, fileSamplers, connection.id)
      val anthropic = connection.fields.copy(format = ApiFormat.AnthropicMessages)
      library.connections.repository.update(connection.id, ConnectionDraft(anthropic))

      library.offer.copy(connection.id, difference.changes)

      assertEquals(Samplers(temperature = 0.85, topP = 0.92), library.connections.stored(connection.id).fields.samplers)
    }
  }

  @Test
  fun theFilesContextSizeAndMaximumReplyTokensAreNeverOffered() = runTest {
    PresetLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      val samplers = library.imported("sillytavern-full.json").samplers
      val difference = found(library, samplers, connection.id)

      val listed = difference.changes.map { fileSampler(it.sampler) } + difference.notCopied.map { it.sampler }
      assertEquals(samplers.values.keys, listed.toSet())
      library.offer.copy(connection.id, difference.changes)
      val copied = library.connections.stored(connection.id).fields
      assertEquals(connection.fields.contextSize, copied.contextSize)
      assertEquals(connection.fields.maxReplyTokens, copied.maxReplyTokens)
    }
  }

  private suspend fun found(library: PresetLibrary, samplers: PresetSamplers, connectionId: Uuid) =
    assertIs<DifferenceResult.Found>(library.offer.difference(samplers, connectionId)).difference

  private suspend fun anthropic(library: PresetLibrary): Connection {
    val created = library.connections.created(draft())
    val anthropic = created.fields.copy(format = ApiFormat.AnthropicMessages)
    library.connections.repository.update(created.id, ConnectionDraft(anthropic))
    return library.connections.stored(created.id)
  }

  private fun fileSampler(sampler: Sampler) = PresetSampler.OnConnection(sampler)

  private fun headerBytes(connection: Connection) = connection.headers.map { it.name to it.value.bytes.toList() }
}

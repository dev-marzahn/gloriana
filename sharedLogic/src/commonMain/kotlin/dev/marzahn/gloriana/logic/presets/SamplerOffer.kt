package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRead
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.SamplerRange
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.SamplersUpdateResult
import dev.marzahn.gloriana.logic.connection.api.SentValue
import dev.marzahn.gloriana.logic.connection.api.sentValue
import dev.marzahn.gloriana.logic.connection.setValues
import dev.marzahn.gloriana.logic.connection.with
import kotlin.uuid.Uuid

data class SamplerChange(val sampler: Sampler, val presetValue: Number, val sentNow: SentValue)

enum class NotCopiedBecause {
  FormatSendsNone,
  OutOfRange,
}

data class NotCopied(val sampler: PresetSampler, val presetValue: Number, val because: NotCopiedBecause)

data class SamplerDifference(val connectionId: Uuid, val changes: List<SamplerChange>, val notCopied: List<NotCopied>) {
  val connectionMatches: Boolean get() = changes.isEmpty()
}

sealed interface DifferenceResult {
  data class Found(val difference: SamplerDifference) : DifferenceResult

  data object ConnectionGone : DifferenceResult

  data object Unreadable : DifferenceResult
}

class SamplerOffer(private val connections: ConnectionRepository, private val global: GlobalModelSettings) {
  suspend fun difference(samplers: PresetSamplers, connectionId: Uuid): DifferenceResult =
    when (val read = connections.read(connectionId)) {
      ConnectionRead.Gone -> DifferenceResult.ConnectionGone
      ConnectionRead.Unreadable -> DifferenceResult.Unreadable
      is ConnectionRead.Readable ->
        DifferenceResult.Found(samplerDifference(samplers, read.connection, global.currentSamplers()))
    }

  suspend fun copy(connectionId: Uuid, accepted: List<SamplerChange>): SamplersUpdateResult =
    connections.updateSamplers(connectionId) { fields ->
      val ranges = fields.format.samplerRanges
      accepted.filter { ranges[it.sampler]?.holds(it.presetValue) == true }
        .fold(fields.samplers) { changed, change -> changed.with(change.sampler, change.presetValue) }
    }
}

private sealed interface Verdict {
  class Copyable(val sampler: Sampler, val value: Number) : Verdict

  class Skipped(val notCopied: NotCopied) : Verdict
}

private fun verdict(sampler: Sampler, value: Number, range: SamplerRange?): Verdict = when {
  range == null -> skipped(sampler, value, NotCopiedBecause.FormatSendsNone)
  !range.holds(value) -> skipped(sampler, value, NotCopiedBecause.OutOfRange)
  else -> Verdict.Copyable(sampler, value)
}

private fun skipped(sampler: Sampler, value: Number, because: NotCopiedBecause) =
  Verdict.Skipped(NotCopied(PresetSampler.OnConnection(sampler), value, because))

internal fun samplerDifference(samplers: PresetSamplers, connection: Connection, global: Samplers): SamplerDifference {
  val format = connection.fields.format
  val ranges = format.samplerRanges
  val verdicts = samplers.onConnection.setValues().map { (sampler, value) -> verdict(sampler, value, ranges[sampler]) }
  val changes = verdicts.filterIsInstance<Verdict.Copyable>().mapNotNull { copyable ->
    val sentNow = sentValue(format, copyable.sampler, global, connection.fields.samplers)
    SamplerChange(copyable.sampler, copyable.value, sentNow).takeIf { sentNow.value != copyable.value }
  }
  val notOnConnections = samplers.withoutConnectionField.map { (sampler, value) ->
    NotCopied(sampler, value, NotCopiedBecause.FormatSendsNone)
  }
  return SamplerDifference(
    connection.id,
    changes,
    verdicts.filterIsInstance<Verdict.Skipped>().map { it.notCopied } + notOnConnections,
  )
}

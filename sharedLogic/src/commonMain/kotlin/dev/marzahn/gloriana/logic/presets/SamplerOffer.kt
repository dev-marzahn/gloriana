package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRead
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.SamplersUpdateResult
import dev.marzahn.gloriana.logic.connection.api.SentValue
import dev.marzahn.gloriana.logic.connection.api.sentValue
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

data class SamplerChange(val sampler: Sampler, val presetValue: Number, val sentNow: SentValue)

enum class NotCopiedBecause {
  FormatSendsNone,
  OutOfRange,
}

data class NotCopied(val sampler: PresetSampler, val because: NotCopiedBecause)

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
        DifferenceResult.Found(samplerDifference(samplers, read.connection, global.samplers().first()))
    }

  suspend fun copy(connectionId: Uuid, accepted: List<SamplerChange>): SamplersUpdateResult =
    connections.updateSamplers(connectionId) { fields ->
      accepted.filter { fields.format.sends(it.sampler, it.presetValue.toDouble()) }
        .fold(fields.samplers) { changed, change -> change.sampler.written(changed, change.presetValue) }
    }
}

private sealed interface Verdict {
  class Copyable(val sampler: Sampler, val value: Double) : Verdict

  class Skipped(val notCopied: NotCopied) : Verdict
}

internal fun samplerDifference(samplers: PresetSamplers, connection: Connection, global: Samplers): SamplerDifference {
  val format = connection.fields.format
  val verdicts = samplers.values.map { (presetSampler, value) ->
    val sampler = (presetSampler as? PresetSampler.OnConnection)?.sampler
    when {
      sampler == null || sampler !in format.samplerRanges ->
        Verdict.Skipped(NotCopied(presetSampler, NotCopiedBecause.FormatSendsNone))
      !format.sends(sampler, value) -> Verdict.Skipped(NotCopied(presetSampler, NotCopiedBecause.OutOfRange))
      else -> Verdict.Copyable(sampler, value)
    }
  }
  val changes = verdicts.filterIsInstance<Verdict.Copyable>().mapNotNull { copyable ->
    val sentNow = sentValue(format, copyable.sampler, global, connection.fields.samplers)
    val presetValue = copyable.sampler.typed(copyable.value)
    SamplerChange(copyable.sampler, presetValue, sentNow).takeIf { sentNow.value != presetValue }
  }
  return SamplerDifference(connection.id, changes, verdicts.filterIsInstance<Verdict.Skipped>().map { it.notCopied })
}

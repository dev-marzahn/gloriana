package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysBlocked
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.SealResult
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.keys.SecretSlot
import kotlin.uuid.Uuid

internal sealed interface SecretPlan {
  class Move(val from: SecretSlot, val to: SecretSlot, val secret: SealedSecret) : SecretPlan
}

internal sealed interface ValuePlan : SecretPlan {
  class Keep(val secret: SealedSecret) : ValuePlan

  class Enter(val slot: SecretSlot, val value: String) : ValuePlan
}

internal class HeaderPlan(val name: String, val value: SecretPlan)

internal class SecretsPlan(val key: SecretPlan?, val headers: List<HeaderPlan>) {
  val holdsAny: Boolean get() = key != null || headers.isNotEmpty()
}

internal sealed interface SecretSource {
  class Typed(val value: String) : SecretSource

  class Stored(val slot: SecretSlot, val secret: SealedSecret) : SecretSource
}

internal class SourcedHeader(val name: String, val source: SecretSource)

internal class SecretSources(val key: SecretSource?, val headers: List<SourcedHeader>)

internal class SourcesLookup(val sources: SecretSources, val gone: Set<ConnectionProblem>)

internal fun Connection.secretSources(): SecretSources = SecretSources(
  key = key?.let { SecretSource.Stored(SecretSlot.ApiKey(id), it) },
  headers = headers.map { SourcedHeader(it.name, storedSource(id, it)) },
)

internal fun lookUpSources(stored: Connection?, draft: ConnectionDraft): SourcesLookup {
  val gone = mutableSetOf<ConnectionProblem>()
  val headers = draft.headers.mapIndexedNotNull { index, header ->
    val source = when (val input = header.value) {
      is HeaderValueInput.Entered -> SecretSource.Typed(input.value)
      is HeaderValueInput.Kept -> stored?.let { owner ->
        owner.headers.firstOrNull { it.name == input.storedName }?.let { storedSource(owner.id, it) }
      }
    }
    if (source == null) gone += ConnectionProblem.HeaderValueGone(index)
    source?.let { SourcedHeader(header.name, it) }
  }
  val key = when (val input = draft.key) {
    KeyInput.Kept -> stored?.secretSources()?.key
    KeyInput.Removed -> null
    is KeyInput.Entered -> SecretSource.Typed(input.value)
  }
  return SourcesLookup(SecretSources(key, headers), gone)
}

private fun storedSource(id: Uuid, header: CustomHeader) =
  SecretSource.Stored(SecretSlot.HeaderValue(id, HeaderName.of(header.name)), header.value)

internal fun secretsPlan(id: Uuid, sources: SecretSources): SecretsPlan = SecretsPlan(
  key = sources.key?.planFor(SecretSlot.ApiKey(id)),
  headers = sources.headers.map { header ->
    HeaderPlan(header.name, header.source.planFor(SecretSlot.HeaderValue(id, HeaderName.of(header.name))))
  },
)

private fun SecretSource.planFor(to: SecretSlot): SecretPlan = when (this) {
  is SecretSource.Typed -> ValuePlan.Enter(to, value)
  is SecretSource.Stored -> if (slot == to) ValuePlan.Keep(secret) else SecretPlan.Move(slot, to, secret)
}

internal sealed interface Sealing {
  class Sealed(val key: SealedSecret?, val headers: List<CustomHeader>) : Sealing

  class Refused(val problems: Set<ConnectionProblem>) : Sealing

  data object NeedsPassphrase : Sealing

  class Blocked(val why: KeysBlocked) : Sealing
}

private sealed interface Step {
  class Value(val secret: SealedSecret) : Step

  class Stop(val sealing: Sealing) : Step
}

internal class ConnectionSecrets(private val vault: KeyVault) {
  suspend fun seal(plan: SecretsPlan): Sealing {
    val key = when (val step = plan.key?.let { step(it, refusedAs = ConnectionProblem.KeyMissing) }) {
      null -> null
      is Step.Value -> step.secret
      is Step.Stop -> return step.sealing
    }
    return sealHeaders(key, plan.headers)
  }

  private suspend fun sealHeaders(key: SealedSecret?, plans: List<HeaderPlan>): Sealing {
    val headers = ArrayList<CustomHeader>(plans.size)
    for ((index, plan) in plans.withIndex()) {
      when (val step = step(plan.value, refusedAs = ConnectionProblem.HeaderValueMissing(index))) {
        is Step.Value -> headers += CustomHeader(plan.name, step.secret)
        is Step.Stop -> return step.sealing
      }
    }
    return Sealing.Sealed(key, headers)
  }

  private suspend fun valueStep(plan: ValuePlan): Step = when (plan) {
    is ValuePlan.Keep -> Step.Value(plan.secret)
    is ValuePlan.Enter -> sealed(plan.slot, plan.value)
  }

  private suspend fun step(plan: SecretPlan, refusedAs: ConnectionProblem): Step = when (plan) {
    is ValuePlan -> valueStep(plan)
    is SecretPlan.Move -> when (val opened = vault.open(plan.from, plan.secret)) {
      is OpenResult.Opened -> sealed(plan.to, opened.value)
      OpenResult.Missing -> Step.Stop(Sealing.Refused(setOf(refusedAs)))
      is OpenResult.Blocked -> Step.Stop(Sealing.Blocked(opened.why))
    }
  }

  private suspend fun sealed(slot: SecretSlot, value: String): Step = when (val sealed = vault.seal(slot, value)) {
    is SealResult.Sealed -> Step.Value(sealed.secret)
    SealResult.NeedsPassphrase -> Step.Stop(Sealing.NeedsPassphrase)
    is SealResult.Blocked -> Step.Stop(Sealing.Blocked(sealed.why))
  }
}

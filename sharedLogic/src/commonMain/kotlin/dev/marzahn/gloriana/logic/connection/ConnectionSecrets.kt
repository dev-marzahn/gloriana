package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyVault
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

internal class SecretsPlan(val key: ValuePlan?, val headers: List<HeaderPlan>, val gone: Set<ConnectionProblem>)

internal fun secretsPlan(id: Uuid, stored: Connection?, draft: ConnectionDraft): SecretsPlan {
  val storedHeaders = stored?.headers.orEmpty().associateBy { it.name }
  val gone = mutableSetOf<ConnectionProblem>()
  val headers = draft.headers.mapIndexedNotNull { index, header ->
    val to = SecretSlot.HeaderValue(id, HeaderName.of(header.name))
    val plan = when (val input = header.value) {
      is HeaderValueInput.Entered -> ValuePlan.Enter(to, input.value)
      is HeaderValueInput.Kept -> storedHeaders[input.storedName]?.let { kept ->
        val from = SecretSlot.HeaderValue(id, HeaderName.of(kept.name))
        if (from == to) ValuePlan.Keep(kept.value) else SecretPlan.Move(from, to, kept.value)
      }
    }
    if (plan == null) gone += ConnectionProblem.HeaderValueGone(index)
    plan?.let { HeaderPlan(header.name, it) }
  }
  val key = when (val input = draft.key) {
    KeyInput.Kept -> stored?.key?.let(ValuePlan::Keep)
    KeyInput.Removed -> null
    is KeyInput.Entered -> ValuePlan.Enter(SecretSlot.ApiKey(id), input.value)
  }
  return SecretsPlan(key, headers, gone)
}

internal sealed interface Sealing {
  class Sealed(val key: SealedSecret?, val headers: List<CustomHeader>) : Sealing

  class Refused(val problems: Set<ConnectionProblem>) : Sealing

  data object Locked : Sealing

  data object Unavailable : Sealing
}

private sealed interface Step {
  class Value(val secret: SealedSecret) : Step

  class Stop(val sealing: Sealing) : Step
}

internal class ConnectionSecrets(private val vault: KeyVault) {
  suspend fun seal(plan: SecretsPlan): Sealing {
    val key = when (val step = plan.key?.let { valueStep(it) }) {
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
      OpenResult.Locked -> Step.Stop(Sealing.Locked)
      OpenResult.Unavailable -> Step.Stop(Sealing.Unavailable)
    }
  }

  private suspend fun sealed(slot: SecretSlot, value: String): Step = when (val sealed = vault.seal(slot, value)) {
    is SealResult.Sealed -> Step.Value(sealed.secret)
    SealResult.Locked -> Step.Stop(Sealing.Locked)
    SealResult.Unavailable -> Step.Stop(Sealing.Unavailable)
  }
}

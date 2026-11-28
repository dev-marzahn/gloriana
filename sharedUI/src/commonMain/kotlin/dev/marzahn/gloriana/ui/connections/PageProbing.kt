package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.api.ConnectionProbe
import dev.marzahn.gloriana.logic.connection.api.ProbeResult
import dev.marzahn.gloriana.logic.connection.api.ProbeWaives
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

internal class PageProbing(
  private val context: PageContext,
  private val probe: ConnectionProbe,
  private val onLocked: (AfterUnlock) -> Unit,
) {
  private val session = context.session

  fun test() {
    val page = context.idlePage() ?: return
    val draft = context.ready(page.form, ProbeWaives.test) ?: return
    val probed = Probed(page.form.keyContext, draft.fields.timeoutSeconds)
    session.update { it.copy(activity = PageActivity.Testing, note = null, test = null) }
    context.scope.launch {
      val started = TimeSource.Monotonic.markNow()
      val step = probe.test(draft, page.saved).step(
        probed,
        done = { reply ->
          { it.copy(activity = PageActivity.Idle, test = TestOutcome.Passed(started.elapsedNow(), reply)) }
        },
        failed = { refusal ->
          when (refusal) {
            is Refusal.Host -> { current ->
              current.copy(activity = PageActivity.Idle, test = TestOutcome.Failed(refusal.failure))
            }
            is Refusal.Keys -> { current -> current.copy(activity = PageActivity.Idle, note = refusal.note) }
          }
        },
      )
      when (step) {
        is ProbeStep.Show -> session.update(step.next)
        is ProbeStep.Refused -> context.showErrors(page.form.errorsOf(step.problems), page.form)
        ProbeStep.Locked -> onLocked(AfterUnlock.Test)
      }
    }
  }

  fun openModelList() {
    val page = context.idlePage() ?: return
    val draft = context.ready(page.form, ProbeWaives.modelList) ?: return
    val probed = Probed(page.form.keyContext, draft.fields.timeoutSeconds)
    val asking = PageActivity.PickingModel(ModelList.Asking)
    session.update { it.copy(activity = asking, modelListNote = null) }
    context.scope.launch {
      val step = probe.models(draft, page.saved).step(
        probed,
        done = { models ->
          if (models.isEmpty()) {
            { it.copy(activity = PageActivity.Idle, modelListNote = ModelListNote.NoneListed) }
          } else {
            { it.copy(activity = PageActivity.PickingModel(ModelList.Listed(models))) }
          }
        },
        failed = { refusal ->
          { current ->
            current.copy(
              activity = PageActivity.PickingModel(ModelList.Failed(refusal)),
              modelListNote = (refusal as? Refusal.Host)?.let { ModelListNote.Failed(it.failure) },
            )
          }
        },
      )
      when (step) {
        is ProbeStep.Show -> session.update { current ->
          if (current.activity == asking) step.next(current) else current
        }
        is ProbeStep.Refused -> context.showErrors(page.form.errorsOf(step.problems), page.form)
        ProbeStep.Locked -> if (session.value.activity == asking) onLocked(AfterUnlock.ModelList)
      }
    }
  }
}

private sealed interface ProbeStep {
  class Show(val next: (PageSession) -> PageSession) : ProbeStep

  class Refused(val problems: Set<ConnectionProblem>) : ProbeStep

  data object Locked : ProbeStep
}

private inline fun <T> ProbeResult<T>.step(
  probed: Probed,
  done: (T) -> (PageSession) -> PageSession,
  failed: (Refusal.Shown) -> (PageSession) -> PageSession,
): ProbeStep = when (this) {
  is ProbeResult.Done -> ProbeStep.Show(done(value))
  is ProbeResult.Refused -> ProbeStep.Refused(problems)
  is ProbeResult.Failed -> when (val refusal = error.refusal(probed)) {
    is Refusal.Shown -> ProbeStep.Show(failed(refusal))
    Refusal.Locked -> ProbeStep.Locked
  }
}

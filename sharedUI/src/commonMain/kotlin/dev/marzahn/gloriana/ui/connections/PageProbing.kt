package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.api.ConnectionProbe
import dev.marzahn.gloriana.logic.connection.api.ProbeResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private val testWaives = setOf(ConnectionProblem.NameEmpty)
private val modelListWaives = testWaives + ConnectionProblem.ModelEmpty

internal class PageProbing(private val context: PageContext, private val probe: ConnectionProbe) {
  private val session = context.session

  fun test() {
    val page = context.idlePage() ?: return
    val draft = context.ready(page.form, testWaives) ?: return
    val probed = Probed(page.form.keyContext, draft.fields.timeoutSeconds, context.held(page))
    session.update { it.copy(activity = PageActivity.Testing, note = null, test = null) }
    context.scope.launch {
      val started = TimeSource.Monotonic.markNow()
      val next: (PageSession) -> PageSession = when (val outcome = probe.test(draft, page.saved)) {
        is ProbeResult.Done -> { current ->
          current.copy(activity = PageActivity.Idle, test = TestOutcome.Passed(started.elapsedNow(), outcome.value))
        }
        is ProbeResult.Refused -> {
          context.showErrors(page.form.errorsOf(outcome.problems), page.form)
          return@launch
        }
        is ProbeResult.Failed -> when (val refusal = outcome.error.refusal(probed)) {
          is Refusal.Host -> { current ->
            current.copy(activity = PageActivity.Idle, test = TestOutcome.Failed(refusal.failure))
          }
          is Refusal.Keys -> { current -> current.copy(activity = PageActivity.Idle, note = refusal.note) }
          Refusal.Locked -> { current ->
            current.copy(activity = PageActivity.Unlocking(UnlockEntry(), AfterUnlock.Test))
          }
        }
      }
      session.update(next)
    }
  }

  fun openModelList() {
    val page = context.idlePage() ?: return
    val draft = context.ready(page.form, modelListWaives) ?: return
    val probed = Probed(page.form.keyContext, draft.fields.timeoutSeconds, context.held(page))
    val asking = PageActivity.PickingModel(ModelList.Asking)
    session.update { it.copy(activity = asking, modelListNote = null) }
    context.scope.launch {
      val next: (PageSession) -> PageSession = when (val outcome = probe.models(draft, page.saved)) {
        is ProbeResult.Done -> if (outcome.value.isEmpty()) {
          { current -> current.copy(activity = PageActivity.Idle, modelListNote = ModelListNote.NoneListed) }
        } else {
          { current -> current.copy(activity = PageActivity.PickingModel(ModelList.Listed(outcome.value))) }
        }
        is ProbeResult.Refused -> {
          context.showErrors(page.form.errorsOf(outcome.problems), page.form)
          return@launch
        }
        is ProbeResult.Failed -> when (val refusal = outcome.error.refusal(probed)) {
          is Refusal.Host -> { current ->
            current.copy(
              activity = PageActivity.PickingModel(ModelList.Failed(refusal)),
              modelListNote = ModelListNote.Failed(refusal.failure),
            )
          }
          is Refusal.Keys -> { current ->
            current.copy(activity = PageActivity.PickingModel(ModelList.Failed(refusal)))
          }
          Refusal.Locked -> { current ->
            current.copy(activity = PageActivity.Unlocking(UnlockEntry(), AfterUnlock.ModelList))
          }
        }
      }
      session.update { current -> if (current.activity == asking) next(current) else current }
    }
  }
}

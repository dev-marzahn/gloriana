package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.KeysUnlock
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.logic.connection.api.ConnectionProbe
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysBlocked
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.StoredRow
import dev.marzahn.gloriana.ui.state.StoredValue
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.UnsavedSaves
import dev.marzahn.gloriana.ui.state.asCell
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias FormChange = (ConnectionForm) -> ConnectionForm

internal data class PageSession(
  val stored: StoredRow<ShownConnection> = StoredValue.Loading,
  val unsaved: UnsavedCopy<ConnectionForm>? = null,
  val errors: FormErrors = FormErrors.None,
  val activity: PageActivity = PageActivity.Idle,
  val test: TestOutcome? = null,
  val note: PageNote? = null,
  val modelListNote: ModelListNote? = null,
) {
  val isIdle: Boolean get() = activity == PageActivity.Idle && unsaved?.isSaving != true

  fun withUnsaved(change: (UnsavedCopy<ConnectionForm>) -> UnsavedCopy<ConnectionForm>) =
    copy(unsaved = unsaved?.let(change))
}

internal class PageContext(
  val session: MutableStateFlow<PageSession>,
  val scope: CoroutineScope,
  val events: Channel<PageEvent>,
  private val state: StateFlow<ConnectionPageState>,
) {
  fun page(): ConnectionPageState.Shown? = state.value as? ConnectionPageState.Shown

  fun idlePage(): ConnectionPageState.Shown? = page()?.takeIf { session.value.isIdle }

  fun ready(form: ConnectionForm, waived: Set<ConnectionProblem> = emptySet()): ConnectionDraft? =
    when (val check = form.check(waived)) {
      is FormCheck.Ready -> check.draft
      is FormCheck.Refused -> null.also { showErrors(check.errors, form) }
    }

  fun showErrors(errors: FormErrors, form: ConnectionForm) {
    session.update { it.copy(activity = PageActivity.Idle, errors = errors) }
    errors.firstIn(form.fieldOrder())?.let { events.trySend(PageEvent.Focus(it)) }
  }

  fun activity(activity: PageActivity) = session.update { it.copy(activity = activity) }
}

private class PageUnlockSlot(private val session: MutableStateFlow<PageSession>) : UnlockPromptSlot {
  override val prompt: UnlockPrompt? get() = (session.value.activity as? PageActivity.Unlocking)?.prompt

  override fun show(prompt: UnlockPrompt?) = session.update { current ->
    val asking = current.activity as? PageActivity.Unlocking ?: return@update current
    current.copy(activity = prompt?.let { asking.copy(prompt = it) } ?: PageActivity.Idle)
  }
}

private class SaveInFlight(val form: ConnectionForm, val draft: ConnectionDraft)

internal class PageWork(
  private val context: PageContext,
  private val repository: ConnectionRepository,
  probe: ConnectionProbe,
  vault: KeyVault,
  keys: KeysUnlock,
  private val commands: ConnectionCommands,
) {
  private val session = context.session
  private val passphrase =
    PagePassphrase(context, vault, UnlockFlow(vault, keys::holding, context.scope, PageUnlockSlot(session)))
  private val probing = PageProbing(context, probe, onLocked = passphrase::ask)
  private val subject = PageSubject(context, commands)
  private val saves = UnsavedSaves(session.asCell(), PageSession::unsaved) { current, unsaved ->
    current.copy(unsaved = unsaved)
  }

  fun on(intent: PageIntent.PageAction, route: ConnectionRoute) {
    when (intent) {
      is PageIntent.Edit -> edit(intent)
      is PageIntent.Toggle -> changeForm(intent) { it }
      PageIntent.Discard -> session.update { current ->
        if (current.activity.locksForm) {
          current
        } else {
          current.withUnsaved { it.discarded() }.copy(errors = FormErrors.None, note = null)
        }
      }
      PageIntent.Save -> save(route)
      PageIntent.Test -> probing.test()
      PageIntent.OpenModelList -> probing.openModelList()
      PageIntent.CloseModelList -> context.activity(PageActivity.Idle)
      is PageIntent.PickModel -> {
        context.activity(PageActivity.Idle)
        edit(PageIntent.Edit(FormField.Model) { it.copy(model = intent.model) })
      }
      is PageIntent.SubjectIntent -> subject.on(intent)
      PageIntent.Unlock -> askToUnlock(AfterUnlock.OnlyUnlock)
      is PageIntent.Passphrase -> passphrase.unlock(intent.intent) { then -> continueWith(then, route) }
      is PageIntent.SetPassphrase -> passphrase.set(intent.intent) { then -> continueWith(then, route) }
    }
  }

  private fun continueWith(then: AfterUnlock, route: ConnectionRoute) {
    when (then) {
      AfterUnlock.Save -> save(route)
      AfterUnlock.Test -> probing.test()
      AfterUnlock.ModelList -> probing.openModelList()
      AfterUnlock.OnlyUnlock -> Unit
      is AfterUnlock.Edit -> applyEdit(then.edit)
    }
  }

  private fun askToUnlock(then: AfterUnlock) {
    context.idlePage() ?: return
    passphrase.ask(then)
  }

  private fun edit(edit: PageIntent.Edit) {
    val page = context.page() ?: return
    if (page.secrets is SecretsState.Locked && page.form.needsKeysToBecome(edit.applied(page.form))) {
      askToUnlock(AfterUnlock.Edit(edit))
    } else {
      applyEdit(edit)
    }
  }

  private fun applyEdit(edit: PageIntent.Edit) = changeForm(edit) { current ->
    current.copy(
      errors = current.errors.without(edit.field),
      modelListNote = current.modelListNote.takeUnless { edit.field == FormField.Model },
    )
  }

  private fun changeForm(edit: PageIntent.FormEdit, adjust: (PageSession) -> PageSession) {
    session.update { current ->
      if (current.activity.locksForm || current.unsaved == null) return@update current
      adjust(current.withUnsaved { it.edited(edit) }.copy(test = null, note = null))
    }
  }

  private fun save(route: ConnectionRoute) {
    val form = session.value.takeIf { it.isIdle }?.unsaved?.draft ?: return
    val draft = context.ready(form) ?: return
    val started = saves.start(
      ready = { current, unsaved -> current.isIdle && unsaved.draft === form },
      started = { it.copy(note = null, test = null) },
    )
    if (started != null) context.scope.launch { finishSave(route, SaveInFlight(form, draft)) }
  }

  private suspend fun finishSave(route: ConnectionRoute, start: SaveInFlight) {
    val result = when (route) {
      is ConnectionRoute.Saved -> repository.update(route.id, start.draft)
      is ConnectionRoute.New -> repository.create(start.draft)
    }
    when (result) {
      is ConnectionSaveResult.Saved -> saved(route, start, result.connection)
      is ConnectionSaveResult.Refused -> notSaved {
        val form = context.page()?.form ?: start.form
        context.showErrors(form.errorsOf(result.problems), form)
      }
      ConnectionSaveResult.NeedsPassphrase ->
        notSaved { context.activity(PageActivity.SettingPassphrase(NewPassphraseEntry())) }
      is ConnectionSaveResult.Blocked -> notSaved {
        when (result.keys) {
          KeysBlocked.Locked -> passphrase.ask(AfterUnlock.Save)
          KeysBlocked.Unavailable -> note(PageNote.KeysBlockedSave(KeysProblem.KeychainUnavailable))
          KeysBlocked.Unreadable -> note(PageNote.KeysBlockedSave(KeysProblem.Unreadable))
        }
      }
      ConnectionSaveResult.StorageFull -> notSaved { note(PageNote.NotSaved(SaveFailure.Storage)) }
      ConnectionSaveResult.Failed, ConnectionSaveResult.Gone, ConnectionSaveResult.Unreadable ->
        notSaved { note(PageNote.NotSaved(SaveFailure.Other)) }
    }
  }

  private suspend fun saved(route: ConnectionRoute, start: SaveInFlight, written: Connection) {
    when (route) {
      is ConnectionRoute.Saved -> {
        saves.saved(ConnectionForm.of(written).withHeaderIdsOf(start.form)) { it.copy(errors = FormErrors.None) }
        commands.saved(written.fields.name)
      }
      is ConnectionRoute.New -> {
        val edits = session.updateAndClaim { current ->
          current.copy(activity = PageActivity.OpeningSaved) to current.unsaved?.editsSinceSaveStarted.orEmpty()
        }
        commands.saved(written.fields.name)
        context.events.send(PageEvent.Saved(ConnectionRoute.Saved(written.id, editsDuringItsFirstSave = edits)))
      }
    }
  }

  private inline fun notSaved(then: () -> Unit) {
    saves.notSaved()
    then()
  }

  private fun note(note: PageNote) = session.update { it.copy(note = note) }
}

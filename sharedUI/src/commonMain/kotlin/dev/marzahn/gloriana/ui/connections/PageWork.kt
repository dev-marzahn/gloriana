package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.api.ConnectionProbe
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val STORED_ROW_WAIT = 2.seconds

typealias FormChange = (ConnectionForm) -> ConnectionForm

sealed interface PageForm {
  data object Untouched : PageForm

  data class Edited(val copy: EditedCopy<ConnectionForm>) : PageForm

  data class Replaying(val changes: List<FormChange>) : PageForm

  fun over(starting: ConnectionForm): EditedCopy<ConnectionForm> = when (this) {
    Untouched -> EditedCopy.of(starting)
    is Edited -> copy
    is Replaying -> EditedCopy(changes.fold(starting) { replayed, change -> change(replayed) }, stored = starting)
  }

  companion object {
    fun followingTheStoredRow(changes: List<FormChange>): PageForm =
      if (changes.isEmpty()) Untouched else Replaying(changes)
  }
}

internal data class PageSession(
  val form: PageForm = PageForm.Untouched,
  val changesDuringSave: List<FormChange> = emptyList(),
  val errors: FormErrors = FormErrors.None,
  val activity: PageActivity = PageActivity.Idle,
  val test: TestOutcome? = null,
  val note: PageNote? = null,
  val modelListNote: ModelListNote? = null,
)

internal class PageContext(
  val session: MutableStateFlow<PageSession>,
  val scope: CoroutineScope,
  val events: Channel<PageEvent>,
  private val state: StateFlow<ConnectionPageState>,
) {
  fun page(): ConnectionPageState.Shown? = state.value as? ConnectionPageState.Shown

  fun idlePage(): ConnectionPageState.Shown? = page()?.takeIf { session.value.activity == PageActivity.Idle }

  fun ready(form: ConnectionForm, waived: Set<ConnectionProblem> = emptySet()): ConnectionDraft? =
    when (val check = form.check(waived)) {
      is FormCheck.Ready -> check.draft
      is FormCheck.Refused -> null.also { showErrors(check.errors, form) }
    }

  fun showErrors(errors: FormErrors, form: ConnectionForm) {
    session.update { it.leaving(PageActivity.Idle).copy(errors = errors) }
    errors.firstIn(form.fieldOrder())?.let { events.trySend(PageEvent.Focus(it)) }
  }

  fun activity(activity: PageActivity) = session.update { it.leaving(activity) }

  suspend fun storedRowShows(after: ConnectionForm, before: ConnectionForm) {
    withTimeoutOrNull(STORED_ROW_WAIT) {
      state.first { current ->
        val live = (current as? ConnectionPageState.Shown)?.saved?.let(ConnectionForm::of)
        live == null || live.sameAs(after) || !live.sameAs(before)
      }
    }
  }
}

private fun PageSession.leaving(next: PageActivity): PageSession =
  copy(activity = next, changesDuringSave = changesDuringSave.takeIf { next == PageActivity.Saving }.orEmpty())

internal class PageWork(
  private val context: PageContext,
  private val repository: ConnectionRepository,
  probe: ConnectionProbe,
  vault: KeyVault,
  commands: ConnectionCommands,
) {
  private val session = context.session
  private val probing = PageProbing(context, probe)
  private val subject = PageSubject(context, commands)
  private val passphrase = PagePassphrase(context, vault, repository)

  fun on(intent: PageIntent.PageAction, route: ConnectionRoute) {
    when (intent) {
      is PageIntent.Edit -> edit(intent.field, intent.change)
      is PageIntent.Toggle -> changeForm(intent.change) { it }
      PageIntent.Discard -> session.update { current ->
        if (current.activity.locksForm) {
          current
        } else {
          current.copy(
            form = PageForm.Untouched,
            changesDuringSave = emptyList(),
            errors = FormErrors.None,
            note = null,
          )
        }
      }
      PageIntent.Save -> save(route)
      PageIntent.Test -> probing.test()
      PageIntent.OpenModelList -> probing.openModelList()
      PageIntent.CloseModelList -> context.activity(PageActivity.Idle)
      is PageIntent.PickModel -> {
        context.activity(PageActivity.Idle)
        edit(FormField.Model) { it.copy(model = intent.model) }
      }
      is PageIntent.SubjectIntent -> subject.on(intent)
      is PageIntent.PassphraseIntent -> passphrase.on(intent) { then -> continueWith(then, route) }
    }
  }

  private fun continueWith(then: AfterUnlock, route: ConnectionRoute) {
    when (then) {
      AfterUnlock.Save -> save(route)
      AfterUnlock.Test -> probing.test()
      AfterUnlock.ModelList -> probing.openModelList()
    }
  }

  private fun edit(field: FormField, change: (ConnectionForm) -> ConnectionForm) = changeForm(change) { current ->
    current.copy(
      errors = current.errors.without(field),
      modelListNote = current.modelListNote.takeUnless { field == FormField.Model },
    )
  }

  private fun changeForm(change: FormChange, adjust: (PageSession) -> PageSession) {
    val shown = context.page() ?: return
    session.update { current ->
      if (current.activity.locksForm) return@update current
      val copy = (current.form as? PageForm.Edited)?.copy ?: shown.copy
      val duringSave =
        if (current.activity == PageActivity.Saving) current.changesDuringSave + change else current.changesDuringSave
      adjust(
        current.copy(
          form = PageForm.Edited(copy.edited(change)),
          changesDuringSave = duringSave,
          test = null,
          note = null,
        ),
      )
    }
  }

  private fun save(route: ConnectionRoute) {
    val page = context.idlePage() ?: return
    val draft = context.ready(page.form) ?: return
    session.update { it.copy(changesDuringSave = emptyList(), note = null, test = null).leaving(PageActivity.Saving) }
    context.scope.launch { finishSave(route, page, draft) }
  }

  private suspend fun settled(route: ConnectionRoute, id: Uuid, before: ConnectionForm?): ConnectionRoute.Saved =
    when (route) {
      is ConnectionRoute.Saved -> {
        val after = failingAs(null) { repository.connection(id).first()?.let(ConnectionForm::of) }
        if (after != null && before != null) context.storedRowShows(after, before)
        session.update { current ->
          current.copy(form = PageForm.followingTheStoredRow(current.changesDuringSave), errors = FormErrors.None)
            .leaving(PageActivity.Idle)
        }
        ConnectionRoute.Saved(id)
      }
      is ConnectionRoute.New -> {
        val changes = session.updateAndClaim { current ->
          current.leaving(PageActivity.OpeningSaved) to current.changesDuringSave
        }
        ConnectionRoute.Saved(id, editsDuringItsFirstSave = changes)
      }
    }

  private suspend fun finishSave(route: ConnectionRoute, page: ConnectionPageState.Shown, draft: ConnectionDraft) {
    val result = when (route) {
      is ConnectionRoute.Saved -> repository.update(route.uuid, draft)
      is ConnectionRoute.New -> repository.create(draft)
    }
    val idle = { change: (PageSession) -> PageSession ->
      session.update { change(it.leaving(PageActivity.Idle)) }
    }
    when (result) {
      is ConnectionSaveResult.Saved -> {
        val saved = settled(route, result.id, before = page.saved?.let(ConnectionForm::of))
        context.events.send(PageEvent.Saved(saved))
      }
      is ConnectionSaveResult.Refused -> {
        val form = context.page()?.form ?: page.form
        context.showErrors(form.errorsOf(result.problems), form)
      }
      ConnectionSaveResult.NeedsPassphrase -> context.activity(PageActivity.SettingPassphrase(NewPassphraseEntry()))
      ConnectionSaveResult.Locked -> context.activity(PageActivity.Unlocking(UnlockEntry(), AfterUnlock.Save))
      ConnectionSaveResult.KeysUnavailable -> idle {
        it.copy(note = PageNote.KeysBlockedSave(KeysProblem.KeychainUnavailable))
      }
      ConnectionSaveResult.KeysUnreadable -> idle { it.copy(note = PageNote.KeysBlockedSave(KeysProblem.Unreadable)) }
      ConnectionSaveResult.StorageFull -> idle { it.copy(note = PageNote.NotSavedStorage) }
      ConnectionSaveResult.Failed, ConnectionSaveResult.Gone, ConnectionSaveResult.Unreadable ->
        idle { it.copy(note = PageNote.NotSaved) }
    }
  }
}

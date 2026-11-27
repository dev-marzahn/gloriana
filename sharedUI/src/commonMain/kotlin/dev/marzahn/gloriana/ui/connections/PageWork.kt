package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.api.ConnectionProbe
import dev.marzahn.gloriana.logic.connection.heldSecrets
import dev.marzahn.gloriana.logic.keys.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PageForm {
  data object Untouched : PageForm

  data class Edited(val copy: EditedCopy<ConnectionForm>) : PageForm

  fun over(starting: ConnectionForm): EditedCopy<ConnectionForm> = when (this) {
    Untouched -> EditedCopy.of(starting)
    is Edited -> copy
  }
}

internal data class PageSession(
  val form: PageForm = PageForm.Untouched,
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
  val page: () -> ConnectionPageState.Shown?,
) {
  fun idlePage(): ConnectionPageState.Shown? = page()?.takeIf { session.value.activity == PageActivity.Idle }

  fun ready(form: ConnectionForm, waived: Set<ConnectionProblem> = emptySet()): ConnectionDraft? =
    when (val check = form.check(waived)) {
      is FormCheck.Ready -> check.draft
      is FormCheck.Refused -> null.also { showErrors(check.errors, form) }
    }

  fun showErrors(errors: FormErrors, form: ConnectionForm) {
    session.update { it.copy(errors = errors, activity = PageActivity.Idle) }
    errors.firstIn(form.fieldOrder())?.let { events.trySend(PageEvent.Focus(it)) }
  }

  fun held(page: ConnectionPageState.Shown): HeldSecrets = page.saved?.heldSecrets ?: HeldSecrets.KeyAndHeaderValues

  fun activity(activity: PageActivity) = session.update { it.copy(activity = activity) }
}

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
      PageIntent.Discard ->
        session.update { it.copy(form = PageForm.Untouched, errors = FormErrors.None, note = null) }
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

  private fun changeForm(change: (ConnectionForm) -> ConnectionForm, adjust: (PageSession) -> PageSession) {
    val shown = context.page() ?: return
    session.update { current ->
      val copy = when (val form = current.form) {
        PageForm.Untouched -> shown.copy
        is PageForm.Edited -> form.copy
      }
      adjust(current.copy(form = PageForm.Edited(copy.edited(change)), test = null, note = null))
    }
  }

  private fun save(route: ConnectionRoute) {
    val page = context.idlePage() ?: return
    val draft = context.ready(page.form) ?: return
    session.update { it.copy(activity = PageActivity.Saving, note = null, test = null) }
    context.scope.launch { finishSave(route, page, draft) }
  }

  private suspend fun finishSave(route: ConnectionRoute, page: ConnectionPageState.Shown, draft: ConnectionDraft) {
    val result = when (route) {
      is ConnectionRoute.Saved -> repository.update(route.uuid, draft)
      is ConnectionRoute.New -> repository.create(draft)
    }
    val idle = { change: (PageSession) -> PageSession ->
      session.update { change(it.copy(activity = PageActivity.Idle)) }
    }
    when (result) {
      is ConnectionSaveResult.Saved -> {
        val settled = when (route) {
          is ConnectionRoute.Saved -> PageForm.Untouched
          is ConnectionRoute.New -> PageForm.Edited(EditedCopy.of(page.form))
        }
        idle { it.copy(form = settled, errors = FormErrors.None) }
        context.events.send(PageEvent.Saved(ConnectionRoute.Saved(result.id)))
      }
      is ConnectionSaveResult.Refused -> context.showErrors(page.form.errorsOf(result.problems), page.form)
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

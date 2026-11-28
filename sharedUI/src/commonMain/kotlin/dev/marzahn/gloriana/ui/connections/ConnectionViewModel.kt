package dev.marzahn.gloriana.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.KeysUnlock
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.logic.connection.api.ConnectionProbe
import dev.marzahn.gloriana.logic.connection.secretChecks
import dev.marzahn.gloriana.logic.connection.secretsState
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.StoredReads
import dev.marzahn.gloriana.ui.state.StoredRow
import dev.marzahn.gloriana.ui.state.StoredValue
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.WhileCollected
import dev.marzahn.gloriana.ui.state.alongside
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface PageIntent {
  sealed interface PageAction : PageIntent

  sealed interface FormIntent : PageAction

  sealed interface FormEdit : FormIntent, DraftEdit<ConnectionForm>

  class Edit(val field: FormField, val change: FormChange) : FormEdit {
    override fun applied(to: ConnectionForm) = change(to)

    override fun replaces(earlier: DraftEdit<ConnectionForm>) = earlier is Edit && earlier.field == field
  }

  class Toggle(val change: FormChange) : FormEdit {
    override fun applied(to: ConnectionForm) = change(to)
  }

  data object Discard : FormIntent

  data object Save : PageAction

  sealed interface ProbeIntent : PageAction

  data object Test : ProbeIntent

  data object OpenModelList : ProbeIntent

  data object CloseModelList : ProbeIntent

  data class PickModel(val model: String) : ProbeIntent

  sealed interface SubjectIntent : PageAction

  data object MakeDefault : SubjectIntent

  data object AskToDelete : SubjectIntent

  data object CancelDelete : SubjectIntent

  data object ConfirmDelete : SubjectIntent

  data object Unlock : PageAction

  data class Passphrase(val intent: PassphraseIntent) : PageAction

  data class SetPassphrase(val intent: NewPassphraseIntent) : PageAction

  data object Retry : PageIntent
}

sealed interface PageEvent {
  data class Focus(val field: FormField) : PageEvent

  data class Saved(val route: ConnectionRoute.Saved) : PageEvent
}

internal class ShownConnection(val connection: Connection?, val isDefault: Boolean)

class ConnectionViewModel(
  private val repository: ConnectionRepository,
  probe: ConnectionProbe,
  private val vault: KeyVault,
  globalSettings: GlobalModelSettings,
  keys: KeysUnlock,
  commands: ConnectionCommands,
) : ViewModel() {
  private val route = MutableStateFlow<ConnectionRoute?>(null)
  private val session = MutableStateFlow(PageSession())
  private val reads = StoredReads.rows<ShownConnection>()
  private val eventChannel = Channel<PageEvent>(Channel.BUFFERED)
  val events: Flow<PageEvent> = eventChannel.receiveAsFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val following: Flow<Unit> = route.filterNotNull().flatMapLatest { route ->
    when (route) {
      is ConnectionRoute.Saved -> vault.secretChecks(followed(route).map { listOfNotNull(it.connectionOrNull()) })
      is ConnectionRoute.New -> emptyFlow()
    }
  }

  val state: StateFlow<ConnectionPageState> =
    combine(session, vault.state, vault.findings, globalSettings.samplers()) { current, vaultState, findings, global ->
      val found = when (val stored = current.stored) {
        StoredValue.Loading -> return@combine ConnectionPageState.Loading
        StoredValue.CouldNotLoad -> return@combine ConnectionPageState.CouldNotLoad
        StoredRow.Gone -> return@combine ConnectionPageState.Gone
        is StoredValue.Found -> stored.value
      }
      ConnectionPageState.Shown(
        saved = found.connection,
        isDefault = found.isDefault,
        unsaved = current.unsaved ?: return@combine ConnectionPageState.Loading,
        errors = current.errors,
        secrets = found.connection?.secretsState(vaultState, findings) ?: SecretsState.Usable,
        globalSamplers = global,
        activity = current.activity,
        test = current.test,
        note = current.note,
        modelListNote = current.modelListNote,
        isIdle = current.isIdle,
      )
    }.alongside(following).stateIn(viewModelScope, WhileCollected, ConnectionPageState.Loading)

  private val work = PageWork(
    PageContext(session, viewModelScope, eventChannel, state),
    repository,
    probe,
    vault,
    keys,
    commands,
  )

  fun show(route: ConnectionRoute) {
    if (this.route.value == route) return
    session.value = when (route) {
      is ConnectionRoute.Saved -> PageSession()
      is ConnectionRoute.New -> PageSession(
        stored = StoredValue.Found(ShownConnection(null, isDefault = false)),
        unsaved = unsavedForm(route.start.form()),
      )
    }
    this.route.value = route
  }

  fun on(intent: PageIntent) {
    val current = route.value ?: return
    when (intent) {
      PageIntent.Retry -> reads.retry()
      is PageIntent.PageAction -> work.on(intent, current)
    }
  }

  private fun followed(route: ConnectionRoute.Saved): Flow<StoredRow<ShownConnection>> = reads.follow(route.id) {
    combine(repository.connection(route.id), repository.defaultConnection()) { saved, default ->
      saved?.let { ShownConnection(it, default == it.id) }
    }
  }.onEach { side ->
    session.update { current ->
      if (this.route.value == route) current.withStored(side, route.editsDuringItsFirstSave) else current
    }
  }
}

private fun StoredRow<ShownConnection>.connectionOrNull(): Connection? = (this as? StoredValue.Found)?.value?.connection

internal fun unsavedForm(stored: ConnectionForm): UnsavedCopy<ConnectionForm> =
  UnsavedCopy.of(stored, ConnectionForm::sameAs)

private fun PageSession.withStored(
  side: StoredRow<ShownConnection>,
  firstEdits: List<DraftEdit<ConnectionForm>>,
): PageSession {
  val connection = side.connectionOrNull() ?: return copy(stored = side)
  val form = ConnectionForm.of(connection).withHeaderIdsOf(unsaved?.beingSaved, unsaved?.draft, unsaved?.baseline)
  val following = unsaved ?: unsavedForm(form).withEdits(firstEdits)
  return copy(stored = side, unsaved = following.storedNow(form))
}

private fun NewConnectionStart.form(): ConnectionForm = when (this) {
  is NewConnectionStart.FromTemplate -> ConnectionForm.of(template)
  NewConnectionStart.FromNothing -> ConnectionForm.fromNothing
}

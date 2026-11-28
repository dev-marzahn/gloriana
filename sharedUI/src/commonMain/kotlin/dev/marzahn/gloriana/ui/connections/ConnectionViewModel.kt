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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

sealed interface PageIntent {
  sealed interface PageAction : PageIntent

  sealed interface FormIntent : PageAction

  class Edit(val field: FormField, val change: FormChange) : FormIntent

  class Toggle(val change: FormChange) : FormIntent

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

private data class PageInputs(val route: ConnectionRoute, val attempt: Int)

private class StoredSide(val connection: Result<Connection?>, val default: Boolean)

class ConnectionViewModel(
  private val repository: ConnectionRepository,
  probe: ConnectionProbe,
  private val vault: KeyVault,
  private val globalSettings: GlobalModelSettings,
  keys: KeysUnlock,
  commands: ConnectionCommands,
) : ViewModel() {
  private val inputs = MutableStateFlow<PageInputs?>(null)
  private val session = MutableStateFlow(PageSession())
  private val eventChannel = Channel<PageEvent>(Channel.BUFFERED)
  private var checks: Job? = null
  val events: Flow<PageEvent> = eventChannel.receiveAsFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<ConnectionPageState> = inputs.filterNotNull()
    .flatMapLatest { (route) ->
      combine(storedSide(route), vault.state, vault.findings, globalSettings.samplers(), session) {
          stored, vaultState, findings, global, current ->
        val connection = stored.connection.getOrElse { return@combine ConnectionPageState.CouldNotLoad }
        val startingForm = when (route) {
          is ConnectionRoute.Saved -> connection?.let(ConnectionForm::of) ?: return@combine ConnectionPageState.Gone
          is ConnectionRoute.New -> route.start.form()
        }
        ConnectionPageState.Shown(
          saved = connection,
          isDefault = stored.default,
          copy = current.form.over(startingForm),
          errors = current.errors,
          secrets = connection?.secretsState(vaultState, findings) ?: SecretsState.Usable,
          globalSamplers = global,
          activity = current.activity,
          test = current.test,
          note = current.note,
          modelListNote = current.modelListNote,
        )
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConnectionPageState.Loading)

  private val work = PageWork(
    PageContext(session, viewModelScope, eventChannel, state),
    repository,
    probe,
    vault,
    keys,
    commands,
  )

  fun show(route: ConnectionRoute) {
    if (inputs.value?.route == route) return
    inputs.value = PageInputs(route, attempt = 0)
    session.value = PageSession(form = startingForm(route))
    checks?.cancel()
    checks = when (route) {
      is ConnectionRoute.Saved -> viewModelScope.launch {
        vault.secretChecks(repository.connection(route.uuid).map(::listOfNotNull).catch { emit(emptyList()) }).collect()
      }
      is ConnectionRoute.New -> null
    }
  }

  fun on(intent: PageIntent) {
    val current = inputs.value ?: return
    when (intent) {
      PageIntent.Retry -> inputs.value = current.copy(attempt = current.attempt + 1)
      is PageIntent.PageAction -> work.on(intent, current.route)
    }
  }

  private fun startingForm(route: ConnectionRoute): PageForm = when (route) {
    is ConnectionRoute.Saved -> PageForm.followingTheStoredRow(route.editsDuringItsFirstSave)
    is ConnectionRoute.New -> PageForm.Untouched
  }

  private fun storedSide(route: ConnectionRoute): Flow<StoredSide> {
    val id = when (route) {
      is ConnectionRoute.Saved -> route.uuid
      is ConnectionRoute.New -> return flowOf(StoredSide(Result.success(null), default = false))
    }
    return combine(
      repository.connection(id).map { Result.success(it) }.catch { emit(Result.failure(it)) },
      repository.defaultConnection().catch { emit(null) },
    ) { saved, default -> StoredSide(saved, default == id) }
  }
}

private fun NewConnectionStart.form(): ConnectionForm = when (this) {
  is NewConnectionStart.FromTemplate -> ConnectionForm.of(template)
  NewConnectionStart.FromNothing -> ConnectionForm.fromNothing
}

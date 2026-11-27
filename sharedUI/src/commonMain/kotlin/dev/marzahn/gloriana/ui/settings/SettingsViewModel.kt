package dev.marzahn.gloriana.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.logic.connection.holdsOpenableSecrets
import dev.marzahn.gloriana.logic.connection.secretChecks
import dev.marzahn.gloriana.logic.connection.secretsState
import dev.marzahn.gloriana.logic.keys.KeyFindings
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.VaultState
import dev.marzahn.gloriana.ui.connections.CommandResult
import dev.marzahn.gloriana.ui.connections.ConnectionCommands
import dev.marzahn.gloriana.ui.connections.WriteFailure
import dev.marzahn.gloriana.ui.connections.failure
import dev.marzahn.gloriana.ui.connections.inListOrder
import dev.marzahn.gloriana.ui.lists.Deleted
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_keys_erased
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

data class ConnectionRowState(val connection: Connection, val isDefault: Boolean, val secrets: SecretsState) {
  val id: Uuid get() = connection.id
}

sealed interface ConnectionsList {
  data object Loading : ConnectionsList

  data object CouldNotLoad : ConnectionsList

  data class Loaded(val rows: List<ConnectionRowState>) : ConnectionsList
}

internal sealed interface StartOverStep {
  data object Idle : StartOverStep

  data class Confirming(val holding: Int?) : StartOverStep

  data class Erasing(val holding: Int?) : StartOverStep

  data class Failed(val failure: WriteFailure) : StartOverStep
}

sealed interface KeysNote {
  val holding: Int?

  data object None : KeysNote {
    override val holding: Int? = null
  }

  data class Unreadable(override val holding: Int) : KeysNote

  data class StartOverFailed(val failure: WriteFailure, override val holding: Int?) : KeysNote
}

data class StartOverSheet(val holding: Int?, val erasing: Boolean)

sealed interface RowActivity {
  data object Idle : RowActivity

  data class MakingDefault(val row: ConnectionRowState) : RowActivity

  data class ConfirmingDelete(val row: ConnectionRowState) : RowActivity

  data class Deleting(val row: ConnectionRowState) : RowActivity

  data class DeleteFailed(val row: ConnectionRowState) : RowActivity

  data class NotMadeDefault(val row: ConnectionRowState) : RowActivity
}

data class SettingsState(
  val connections: ConnectionsList = ConnectionsList.Loading,
  val showsPassphrase: Boolean = false,
  val keys: KeysNote = KeysNote.None,
  val startOver: StartOverSheet? = null,
  val rowActivity: RowActivity = RowActivity.Idle,
  val focus: ListFocus = ListFocus.None,
)

sealed interface ListFocus {
  data object None : ListFocus

  data class Row(val index: Int) : ListFocus
}

sealed interface SettingsIntent {
  data object Retry : SettingsIntent

  data class MakeDefault(val row: ConnectionRowState) : SettingsIntent

  data class AskToDelete(val row: ConnectionRowState) : SettingsIntent

  data object CancelDelete : SettingsIntent

  data object ConfirmDelete : SettingsIntent

  data object AskToStartOver : SettingsIntent

  data object CancelStartOver : SettingsIntent

  data object ConfirmStartOver : SettingsIntent

  data object Focused : SettingsIntent
}

private data class SettingsSession(
  val startOver: StartOverStep = StartOverStep.Idle,
  val rows: RowActivity = RowActivity.Idle,
  val focusFirstRow: Boolean = false,
)

private class Stored(val connections: List<Connection>, val default: Uuid?)

class SettingsViewModel(
  private val repository: ConnectionRepository,
  private val vault: KeyVault,
  private val commands: ConnectionCommands,
  private val messages: AppMessages,
) : ViewModel() {
  private val attempts = MutableStateFlow(0)
  private val session = MutableStateFlow(SettingsSession())

  @OptIn(ExperimentalCoroutinesApi::class)
  private val stored: Flow<Result<Stored>> = attempts.flatMapLatest {
    combine(repository.connections(), repository.defaultConnection(), ::Stored)
      .map { Result.success(it) }
      .catch { emit(Result.failure(it)) }
  }

  val state: StateFlow<SettingsState> =
    combine(stored, vault.state, vault.findings, session, commands.deletions.latest) { loaded, vaultState, findings,
                                                                                    current, deleted ->
      val list = loaded.fold(
        onSuccess = { stored ->
          ConnectionsList.Loaded(
            stored.connections.inListOrder().map { connection ->
              val secrets = connection.secretsState(vaultState, findings)
              ConnectionRowState(connection, connection.id == stored.default, secrets)
            },
          )
        },
        onFailure = { ConnectionsList.CouldNotLoad },
      )
      SettingsState(
        connections = list,
        showsPassphrase = vaultState.hasPassphrase,
        keys = keysNote(list.holding(findings), vaultState, current.startOver),
        startOver = current.startOver.sheet(),
        rowActivity = current.rows,
        focus = focus(list, deleted, current.focusFirstRow),
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsState())

  init {
    viewModelScope.launch { vault.prepareIfStored() }
    viewModelScope.launch { vault.secretChecks(repository.connections().catch { emit(emptyList()) }).collect() }
  }

  fun on(intent: SettingsIntent) {
    when (intent) {
      SettingsIntent.Retry -> attempts.update { it + 1 }
      is SettingsIntent.MakeDefault -> makeDefault(intent.row)
      is SettingsIntent.AskToDelete -> rows(RowActivity.ConfirmingDelete(intent.row))
      SettingsIntent.CancelDelete -> rows(RowActivity.Idle)
      SettingsIntent.ConfirmDelete -> confirmDelete()
      SettingsIntent.AskToStartOver -> startOver(StartOverStep.Confirming(state.value.keys.holding))
      SettingsIntent.CancelStartOver -> startOver(StartOverStep.Idle)
      SettingsIntent.ConfirmStartOver -> confirmStartOver()
      SettingsIntent.Focused -> {
        commands.deletions.latest.value?.let(commands.deletions::handled)
        session.update { it.copy(focusFirstRow = false) }
      }
    }
  }

  private fun rows(activity: RowActivity) = session.update { it.copy(rows = activity) }

  private fun startOver(step: StartOverStep) = session.update { it.copy(startOver = step) }

  private fun makeDefault(row: ConnectionRowState) {
    if (session.value.rows is RowActivity.MakingDefault) return
    rows(RowActivity.MakingDefault(row))
    viewModelScope.launch {
      when (commands.makeDefault(row.connection)) {
        CommandResult.Done -> rows(RowActivity.Idle)
        CommandResult.Failed -> rows(RowActivity.NotMadeDefault(row))
      }
    }
  }

  private fun confirmDelete() {
    val row = session.updateAndClaim { current ->
      (current.rows as? RowActivity.ConfirmingDelete)?.row
        ?.let { current.copy(rows = RowActivity.Deleting(it)) to it }
        ?: (current to null)
    } ?: return
    viewModelScope.launch {
      when (commands.delete(row.connection)) {
        CommandResult.Done -> rows(RowActivity.Idle)
        CommandResult.Failed -> rows(RowActivity.DeleteFailed(row))
      }
    }
  }

  private fun confirmStartOver() {
    session.updateAndClaim { current ->
      (current.startOver as? StartOverStep.Confirming)
        ?.let { current.copy(startOver = StartOverStep.Erasing(it.holding)) to it }
        ?: (current to null)
    } ?: return
    viewModelScope.launch {
      val result = vault.replaceDeviceKeyset()
      when (val failure = result.failure()) {
        null -> {
          session.update { it.copy(startOver = StartOverStep.Idle, focusFirstRow = true) }
          messages.show(AppMessage(getString(Res.string.snack_keys_erased)))
        }
        WriteFailure.NothingChanged -> startOver(StartOverStep.Failed(failure))
        WriteFailure.KeysErased -> {
          vault.prepare()
          startOver(StartOverStep.Failed(failure))
        }
      }
    }
  }
}

private fun ConnectionsList.holding(findings: KeyFindings): Int? = when (this) {
  is ConnectionsList.Loaded -> rows.count { findings.holdsOpenableSecrets(it.connection) }
  ConnectionsList.Loading, ConnectionsList.CouldNotLoad -> null
}

internal fun keysNote(holding: Int?, vault: VaultState, step: StartOverStep): KeysNote = when {
  step is StartOverStep.Failed -> KeysNote.StartOverFailed(step.failure, holding)
  vault.needsStartingOver && holding != null -> KeysNote.Unreadable(holding)
  else -> KeysNote.None
}

private fun StartOverStep.sheet(): StartOverSheet? = when (this) {
  is StartOverStep.Confirming -> StartOverSheet(holding, erasing = false)
  is StartOverStep.Erasing -> StartOverSheet(holding, erasing = true)
  StartOverStep.Idle, is StartOverStep.Failed -> null
}

private fun focus(list: ConnectionsList, deleted: Deleted?, focusFirstRow: Boolean): ListFocus {
  val rows = when (list) {
    is ConnectionsList.Loaded -> list.rows
    ConnectionsList.Loading, ConnectionsList.CouldNotLoad -> return ListFocus.None
  }
  return when {
    deleted != null && rows.none { it.id == deleted.id } -> ListFocus.Row(deleted.index)
    focusFirstRow -> ListFocus.Row(0)
    else -> ListFocus.None
  }
}

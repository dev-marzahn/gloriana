package dev.marzahn.gloriana.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.logic.connection.countHolding
import dev.marzahn.gloriana.logic.connection.secretChecks
import dev.marzahn.gloriana.logic.connection.secretsState
import dev.marzahn.gloriana.logic.keys.KeyFindings
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysOpening
import dev.marzahn.gloriana.logic.keys.VaultState
import dev.marzahn.gloriana.ui.connections.CommandResult
import dev.marzahn.gloriana.ui.connections.ConnectionCommands
import dev.marzahn.gloriana.ui.connections.PassphraseIntent
import dev.marzahn.gloriana.ui.connections.UnlockFlow
import dev.marzahn.gloriana.ui.connections.UnlockPrompt
import dev.marzahn.gloriana.ui.connections.UnlockPromptSlot
import dev.marzahn.gloriana.ui.connections.WriteFailure
import dev.marzahn.gloriana.ui.connections.failure
import dev.marzahn.gloriana.ui.lists.Deleted
import dev.marzahn.gloriana.ui.lists.UserOrderMoves
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_keys_erased
import dev.marzahn.gloriana.ui.state.WhileCollected
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

  data object Confirming : StartOverStep

  data class Erasing(val holding: Int?) : StartOverStep

  data class Failed(val failure: WriteFailure) : StartOverStep
}

sealed interface KeysNote {
  data object None : KeysNote

  data class Unreadable(val holding: Int) : KeysNote

  data object UnreadableWhileTheListCantLoad : KeysNote

  data class StartOverFailed(val failure: WriteFailure, val holding: Int?) : KeysNote
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
  val holding: Int? = null,
  val keys: KeysNote = KeysNote.None,
  val locked: List<String> = emptyList(),
  val unlock: UnlockPrompt? = null,
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

  data object AskToUnlock : SettingsIntent

  data class Passphrase(val intent: PassphraseIntent) : SettingsIntent

  data object Focused : SettingsIntent
}

private data class SettingsSession(
  val startOver: StartOverStep = StartOverStep.Idle,
  val unlock: UnlockPrompt? = null,
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

  val moves = UserOrderMoves(viewModelScope, repository.order, repository.connections(), Connection::id)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val stored: Flow<Result<Stored>> = attempts.flatMapLatest {
    combine(moves.arranged, repository.defaultConnection(), ::Stored)
      .map { Result.success(it) }
      .catch { emit(Result.failure(it)) }
  }

  val state: StateFlow<SettingsState> =
    combine(stored, vault.state, vault.findings, session, commands.deletions.latest) { loaded, vaultState, findings,
                                                                                    current, deleted ->
      val list = loaded.fold(
        onSuccess = { stored ->
          ConnectionsList.Loaded(
            stored.connections.map { connection ->
              val secrets = connection.secretsState(vaultState, findings)
              ConnectionRowState(connection, connection.id == stored.default, secrets)
            },
          )
        },
        onFailure = { ConnectionsList.CouldNotLoad },
      )
      val holding = list.holding(findings)
      SettingsState(
        connections = list,
        showsPassphrase = vaultState.hasPassphrase,
        holding = holding,
        keys = keysNote(holding, vaultState, current.startOver),
        locked = list.lockedNames(),
        unlock = current.unlock,
        startOver = current.startOver.sheet(holding),
        rowActivity = current.rows,
        focus = focus(list, deleted, current.focusFirstRow),
      )
    }.stateIn(viewModelScope, WhileCollected, SettingsState())

  private val unlocking = UnlockFlow(
    vault,
    { state.value.holding },
    viewModelScope,
    object : UnlockPromptSlot {
      override val prompt: UnlockPrompt? get() = session.value.unlock

      override fun show(prompt: UnlockPrompt?) = session.update { it.copy(unlock = prompt) }
    },
  )

  init {
    viewModelScope.launch { vault.prepare(KeysOpening.IfStored) }
    viewModelScope.launch {
      state.collect { shown -> if (shown.connections !is ConnectionsList.Loaded) closeStartOverQuestion() }
    }
    viewModelScope.launch { vault.secretChecks(repository.connections().catch { emit(emptyList()) }).collect() }
  }

  fun on(intent: SettingsIntent) {
    when (intent) {
      SettingsIntent.Retry -> attempts.update { it + 1 }
      is SettingsIntent.MakeDefault -> makeDefault(intent.row)
      is SettingsIntent.AskToDelete -> rows(RowActivity.ConfirmingDelete(intent.row))
      SettingsIntent.CancelDelete -> rows(RowActivity.Idle)
      SettingsIntent.ConfirmDelete -> confirmDelete()
      SettingsIntent.AskToStartOver -> startOver(StartOverStep.Confirming)
      SettingsIntent.CancelStartOver -> startOver(StartOverStep.Idle)
      SettingsIntent.ConfirmStartOver -> confirmStartOver()
      SettingsIntent.AskToUnlock -> unlocking.open { prompt ->
        session.update { current -> if (current.unlock == null) current.copy(unlock = prompt) else current }
      }
      is SettingsIntent.Passphrase -> unlocking.on(intent.intent) {}
      SettingsIntent.Focused -> {
        commands.deletions.latest.value?.let(commands.deletions::handled)
        session.update { it.copy(focusFirstRow = false) }
      }
    }
  }

  private fun rows(activity: RowActivity) = session.update { it.copy(rows = activity) }

  private fun startOver(step: StartOverStep) = session.update { it.copy(startOver = step) }

  private fun closeStartOverQuestion() = session.update { current ->
    if (current.startOver is StartOverStep.Confirming) current.copy(startOver = StartOverStep.Idle) else current
  }

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
    val holding = state.value.holding
    session.updateAndClaim { current ->
      if (current.startOver == StartOverStep.Confirming) {
        current.copy(startOver = StartOverStep.Erasing(holding)) to Unit
      } else {
        current to null
      }
    } ?: return
    viewModelScope.launch {
      when (val failure = vault.replaceDeviceKeyset().failure()) {
        null -> keysErased()
        WriteFailure.NothingChanged -> startOver(StartOverStep.Failed(failure))
        WriteFailure.KeysErased ->
          if (vault.prepare() == VaultState.Unlocked) keysErased() else startOver(StartOverStep.Failed(failure))
      }
    }
  }

  private suspend fun keysErased() {
    session.update { it.copy(startOver = StartOverStep.Idle, focusFirstRow = true) }
    messages.show(AppMessage(getString(Res.string.snack_keys_erased)))
  }
}

private fun ConnectionsList.holding(findings: KeyFindings): Int? = when (this) {
  is ConnectionsList.Loaded -> findings.countHolding(rows.map { it.connection })
  ConnectionsList.Loading, ConnectionsList.CouldNotLoad -> null
}

private fun ConnectionsList.lockedNames(): List<String> = when (this) {
  is ConnectionsList.Loaded -> rows.filter { it.secrets is SecretsState.Locked }.map { it.connection.fields.name }
  ConnectionsList.Loading, ConnectionsList.CouldNotLoad -> emptyList()
}

internal fun keysNote(holding: Int?, vault: VaultState, step: StartOverStep): KeysNote = when {
  step is StartOverStep.Failed -> KeysNote.StartOverFailed(step.failure, holding)
  !vault.needsStartingOver -> KeysNote.None
  holding == null -> KeysNote.UnreadableWhileTheListCantLoad
  else -> KeysNote.Unreadable(holding)
}

private fun StartOverStep.sheet(holdingNow: Int?): StartOverSheet? = when (this) {
  StartOverStep.Confirming -> StartOverSheet(holdingNow, erasing = false)
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

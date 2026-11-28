package dev.marzahn.gloriana.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_passphrase_changed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class ChangePassphrase(
  val current: SecretText = SecretText.Empty,
  val currentWrong: Boolean = false,
  val entry: NewPassphraseEntry = NewPassphraseEntry(),
)

sealed interface ChangeIntent {
  data class TypeCurrent(val text: SecretText) : ChangeIntent

  data class TypeNew(val text: SecretText) : ChangeIntent

  data class TypeAgain(val text: SecretText) : ChangeIntent

  data object Change : ChangeIntent
}

class PassphraseViewModel(private val vault: KeyVault, private val messages: AppMessages) : ViewModel() {
  private val mutableState = MutableStateFlow(ChangePassphrase())
  private val closeChannel = Channel<Unit>(Channel.BUFFERED)

  val state: StateFlow<ChangePassphrase> = mutableState.asStateFlow()

  val closeRequests: Flow<Unit> = closeChannel.receiveAsFlow()

  fun on(intent: ChangeIntent) {
    when (intent) {
      is ChangeIntent.TypeCurrent -> mutableState.update {
        it.copy(current = intent.text, currentWrong = false)
      }
      is ChangeIntent.TypeNew -> mutableState.update { it.copy(entry = it.entry.typedPassphrase(intent.text)) }
      is ChangeIntent.TypeAgain -> mutableState.update { it.copy(entry = it.entry.typedAgain(intent.text)) }
      ChangeIntent.Change -> change()
    }
  }

  private fun change() {
    val change = mutableState.value.takeUnless { it.entry.busy } ?: return
    viewModelScope.launch {
      val submitted = change.entry.submit(
        show = { shown -> mutableState.update { it.copy(entry = shown, currentWrong = false) } },
        write = { new -> vault.changePassphrase(change.current.text.toCharArray(), new) },
        failureOf = PassphraseResult::failure,
      )
      val result = when (submitted) {
        is Submitted.Written -> submitted.result
        Submitted.Refused -> return@launch
      }
      when (result) {
        PassphraseResult.Accepted -> {
          mutableState.value = ChangePassphrase()
          messages.show(AppMessage(getString(Res.string.snack_passphrase_changed)))
          closeChannel.send(Unit)
        }
        PassphraseResult.WrongPassphrase -> mutableState.update {
          it.copy(currentWrong = true, entry = it.entry.copy(step = EntryStep.Typing))
        }
        PassphraseResult.NotAvailable -> Unit
      }
    }
  }
}

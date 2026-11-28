package dev.marzahn.gloriana.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.countHoldingOpenableSecrets
import dev.marzahn.gloriana.logic.keys.ErasingResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysOpening
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.keys.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResetSheet {
  data object Closed : ResetSheet

  data class Open(val entry: NewPassphraseEntry) : ResetSheet
}

sealed interface KeysGate {
  data object Checking : KeysGate

  data object Open : KeysGate

  sealed interface Shown : KeysGate {
    val canSkip: Boolean
      get() = when (this) {
        is Unlocking -> !entry.checking && !reset.isBusy
        is Damaged -> !reset.isBusy
        CouldNotCheck -> true
      }
  }

  data object CouldNotCheck : Shown

  sealed interface Asking : Shown {
    val holding: Int
    val reset: ResetSheet
  }

  data class Unlocking(
    override val holding: Int,
    val entry: UnlockEntry = UnlockEntry(),
    override val reset: ResetSheet = ResetSheet.Closed,
  ) : Asking

  data class Damaged(override val holding: Int, override val reset: ResetSheet = ResetSheet.Closed) : Asking
}

sealed interface GateIntent {
  data class TypePassphrase(val text: SecretText) : GateIntent

  data object Unlock : GateIntent

  data object OpenReset : GateIntent

  data object CloseReset : GateIntent

  data class TypeNewPassphrase(val text: SecretText) : GateIntent

  data class TypeNewPassphraseAgain(val text: SecretText) : GateIntent

  data object Reset : GateIntent

  data object Retry : GateIntent

  data object NotNow : GateIntent
}

class KeysGateViewModel(
  private val repository: ConnectionRepository,
  private val vault: KeyVault,
) : ViewModel() {
  private val mutableGate = MutableStateFlow<KeysGate>(KeysGate.Checking)

  val gate: StateFlow<KeysGate> = mutableGate.asStateFlow()

  init {
    check()
  }

  fun on(intent: GateIntent) {
    when (intent) {
      is GateIntent.TypePassphrase -> unlocking { it.copy(entry = it.entry.typed(intent.text)) }
      GateIntent.Unlock -> unlock()
      GateIntent.OpenReset -> resetSheet { ResetSheet.Open(NewPassphraseEntry()) }
      GateIntent.CloseReset -> resetSheet { sheet -> if (sheet.isBusy) sheet else ResetSheet.Closed }
      is GateIntent.TypeNewPassphrase -> resetEntry { it.typedPassphrase(intent.text) }
      is GateIntent.TypeNewPassphraseAgain -> resetEntry { it.typedAgain(intent.text) }
      GateIntent.Reset -> reset()
      GateIntent.Retry -> {
        mutableGate.value = KeysGate.Checking
        check()
      }
      GateIntent.NotNow -> notNow()
    }
  }

  private fun check() {
    viewModelScope.launch {
      mutableGate.value = when (vault.prepare(KeysOpening.IfPassphraseProtected)) {
        VaultState.Locked -> holdingSecrets()?.let { KeysGate.Unlocking(it) } ?: KeysGate.CouldNotCheck
        VaultState.PassphraseDamaged -> holdingSecrets()?.let { KeysGate.Damaged(it) } ?: KeysGate.CouldNotCheck
        VaultState.Unavailable -> KeysGate.CouldNotCheck
        VaultState.Closed, VaultState.Unlocked, VaultState.PassphraseUnlocked, VaultState.NoKeychain,
        VaultState.DeviceDamaged, VaultState.DeviceKeyNotFound,
        -> KeysGate.Open
      }
    }
  }

  private fun notNow() {
    if ((mutableGate.value as? KeysGate.Shown)?.canSkip != true) return
    vault.deferUnlock()
    mutableGate.value = KeysGate.Open
  }

  private suspend fun holdingSecrets(): Int? = vault.countHoldingOpenableSecrets(repository.connections())

  private fun unlock() {
    val asking = mutableGate.value as? KeysGate.Unlocking ?: return
    if (asking.entry.checking) return
    viewModelScope.launch {
      val result = asking.entry.submit({ shown -> unlocking { it.copy(entry = shown) } }) { vault.unlock(it) }
      when (result) {
        PassphraseResult.Accepted -> mutableGate.value = KeysGate.Open
        PassphraseResult.WrongPassphrase -> Unit
        PassphraseResult.NotAvailable -> mutableGate.value = KeysGate.CouldNotCheck
      }
    }
  }

  private fun reset() {
    val entry = ((mutableGate.value as? KeysGate.Asking)?.reset as? ResetSheet.Open)?.entry?.takeUnless { it.busy }
      ?: return
    viewModelScope.launch {
      val submitted = entry.submit(
        show = { shown -> resetSheet { ResetSheet.Open(shown) } },
        write = { vault.reset(it) },
        failureOf = ErasingResult::failure,
      )
      if (submitted == Submitted.Written(ErasingResult.Done)) mutableGate.value = KeysGate.Open
    }
  }

  private fun unlocking(change: (KeysGate.Unlocking) -> KeysGate.Unlocking) {
    mutableGate.update { (it as? KeysGate.Unlocking)?.let(change) ?: it }
  }

  private fun resetSheet(change: (ResetSheet) -> ResetSheet) {
    mutableGate.update { gate ->
      when (gate) {
        is KeysGate.Unlocking -> gate.copy(reset = change(gate.reset))
        is KeysGate.Damaged -> gate.copy(reset = change(gate.reset))
        KeysGate.Checking, KeysGate.Open, KeysGate.CouldNotCheck -> gate
      }
    }
  }

  private fun resetEntry(change: (NewPassphraseEntry) -> NewPassphraseEntry) {
    resetSheet { sheet ->
      when (sheet) {
        is ResetSheet.Open -> ResetSheet.Open(change(sheet.entry))
        ResetSheet.Closed -> sheet
      }
    }
  }
}

private val ResetSheet.isBusy: Boolean
  get() = when (this) {
    is ResetSheet.Open -> entry.busy
    ResetSheet.Closed -> false
  }

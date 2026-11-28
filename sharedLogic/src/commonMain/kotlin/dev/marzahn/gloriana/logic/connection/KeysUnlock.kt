package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysOpening
import dev.marzahn.gloriana.logic.keys.VaultState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

sealed interface PassphraseAsk {
  data object None : PassphraseAsk

  data object CouldNotCheck : PassphraseAsk

  data class Unlock(val holding: Int) : PassphraseAsk

  data class Damaged(val holding: Int) : PassphraseAsk
}

class KeysUnlock(private val vault: KeyVault, private val repository: ConnectionRepository) {
  suspend fun holding(): Int? = failingAs(null) {
    combine(repository.connections(), vault.findings) { connections, findings -> findings.countHolding(connections) }
      .first()
  }

  suspend fun askAtLaunch(): PassphraseAsk = when (vault.prepare(KeysOpening.IfPassphraseProtected)) {
    VaultState.Locked -> counted(PassphraseAsk::Unlock)
    VaultState.PassphraseDamaged -> counted(PassphraseAsk::Damaged)
    VaultState.Unavailable -> PassphraseAsk.CouldNotCheck
    VaultState.Closed, VaultState.Unlocked, VaultState.PassphraseUnlocked, VaultState.NoKeychain,
    VaultState.DeviceDamaged, VaultState.DeviceKeyNotFound,
    -> PassphraseAsk.None
  }

  private suspend fun counted(ask: (Int) -> PassphraseAsk): PassphraseAsk =
    holding()?.let(ask) ?: PassphraseAsk.CouldNotCheck
}

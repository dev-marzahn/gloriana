package dev.marzahn.gloriana.logic.keys

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

internal class KeysetHolder(private val keyring: Keyring) {
  private val mutex = Mutex()
  private val mutableState = MutableStateFlow(VaultState.Closed)

  val state: StateFlow<VaultState> = mutableState.asStateFlow()

  var keyset: Keyset = Keyset.Closed
    set(value) {
      field = value
      mutableState.value = value.state
    }

  suspend fun <T> withFiles(action: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { action() } }

  suspend fun <T> withKeyset(action: (Keyset) -> T): T = withFiles {
    if (keyset.isRetried()) keyset = keyring.open()
    action(keyset)
  }

  suspend fun rewriting(applies: (Keyset) -> Boolean, write: (Keyset) -> PassphraseResult): PassphraseResult =
    withKeyset { current ->
      if (!applies(current)) return@withKeyset PassphraseResult.NotAvailable
      try {
        write(current)
      } catch (_: IOException) {
        keyset = Keyset.Closed
        PassphraseResult.NotAvailable
      }
    }

  suspend fun erasing(rewrite: (Keyset) -> Rewrite?): ErasingResult = withKeyset { current ->
    when (val result = rewrite(current)) {
      is Rewrite.Written -> ErasingResult.Done.also { keyset = result.keyset }
      Rewrite.NothingErased, null -> ErasingResult.NothingChanged
      Rewrite.ErasedNotWritten -> ErasingResult.KeysErasedKeysetKept.also { keyset = Keyset.Closed }
    }
  }
}

private fun Keyset.isRetried(): Boolean =
  this == Keyset.Closed || this == Keyset.Unavailable || this is Keyset.DeviceKeyNotFound

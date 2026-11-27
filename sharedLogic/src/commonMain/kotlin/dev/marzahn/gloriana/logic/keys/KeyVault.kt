package dev.marzahn.gloriana.logic.keys

import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

interface KeyVault {
  val state: StateFlow<VaultState>

  suspend fun prepare(): VaultState

  suspend fun seal(slot: SecretSlot, value: String): SealResult

  suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult

  suspend fun erase(connectionId: Uuid): KeyFileResult

  suspend fun setPassphrase(passphrase: CharArray): PassphraseResult

  suspend fun unlock(passphrase: CharArray): PassphraseResult

  suspend fun changePassphrase(current: CharArray, new: CharArray): PassphraseResult

  suspend fun reset(newPassphrase: CharArray): PassphraseResult

  suspend fun replaceDeviceKeyset(): PassphraseResult
}

@JvmInline
value class HeaderName private constructor(val canonical: String) {
  companion object {
    fun of(name: String) = HeaderName(name.lowercase())
  }
}

enum class KeyFileResult {
  Done,
  Unavailable,
}

sealed interface SecretSlot {
  val connectionId: Uuid

  data class ApiKey(override val connectionId: Uuid) : SecretSlot

  data class HeaderValue(override val connectionId: Uuid, val name: HeaderName) : SecretSlot
}

class SealedSecret(val bytes: ByteArray)

sealed interface SealResult {
  class Sealed(val secret: SealedSecret) : SealResult

  data object Locked : SealResult

  data object Unavailable : SealResult
}

sealed interface OpenResult {
  class Opened(val value: String) : OpenResult

  data object Missing : OpenResult

  data object Locked : OpenResult

  data object Unavailable : OpenResult
}

enum class VaultState {
  Closed,
  Unlocked,
  PassphraseUnlocked,
  Locked,
  NoKeychain,
  Unavailable,
  PassphraseDamaged,
  DeviceDamaged,
  DeviceKeyNotFound,
}

enum class PassphraseResult {
  Accepted,
  WrongPassphrase,
  NotAvailable,
}

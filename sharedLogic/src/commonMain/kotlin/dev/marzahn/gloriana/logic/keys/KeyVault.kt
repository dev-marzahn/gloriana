package dev.marzahn.gloriana.logic.keys

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

interface KeyVault {
  val state: StateFlow<VaultState>

  val findings: Flow<KeyFindings>

  suspend fun prepare(): VaultState

  suspend fun prepareIfStored(): VaultState

  suspend fun prepareIfPassphraseProtected(): VaultState

  suspend fun seal(slot: SecretSlot, value: String): SealResult

  suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult

  suspend fun erase(connectionId: Uuid): KeyFileResult

  suspend fun setPassphrase(passphrase: CharArray): PassphraseResult

  suspend fun unlock(passphrase: CharArray): PassphraseResult

  suspend fun changePassphrase(current: CharArray, new: CharArray): PassphraseResult

  suspend fun reset(newPassphrase: CharArray): ErasingResult

  suspend fun replaceDeviceKeyset(): ErasingResult
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

  data object NeedsPassphrase : SealResult

  data object Locked : SealResult

  data object Unavailable : SealResult

  data object Unreadable : SealResult
}

sealed interface OpenResult {
  class Opened(val value: String) : OpenResult

  data object Missing : OpenResult

  data object Locked : OpenResult

  data object Unavailable : OpenResult

  data object Unreadable : OpenResult
}

enum class VaultState(val hasPassphrase: Boolean = false, val needsStartingOver: Boolean = false) {
  Closed,
  Unlocked,
  PassphraseUnlocked(hasPassphrase = true),
  Locked(hasPassphrase = true),
  NoKeychain,
  Unavailable,
  PassphraseDamaged(hasPassphrase = true),
  DeviceDamaged(needsStartingOver = true),
  DeviceKeyNotFound(needsStartingOver = true),
}

enum class PassphraseResult {
  Accepted,
  WrongPassphrase,
  NotAvailable,
}

enum class ErasingResult {
  Done,
  NothingChanged,
  KeysErasedKeysetKept,
}

package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.Aead
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.Faults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.nio.file.Path
import java.security.GeneralSecurityException
import kotlin.uuid.Uuid

private const val KEYS_DIRECTORY = "keys"
private const val DATA_KEYS_FILE = "connections.json"
private const val API_KEY_TAG: Byte = 1
private const val HEADER_VALUE_TAG: Byte = 2

internal fun keysDirectoryIn(library: Path): Path = library.resolve(KEYS_DIRECTORY)

internal class TinkKeyVault(
  library: LibraryDirectory,
  device: DeviceKeyStore,
  private val faults: Faults,
  stored: StoredConnections,
  passphraseCost: Argon2idCost = Argon2idCost.Default,
  private val derivation: KeyDerivation = KeyDerivation.Argon2id,
) : KeyVault {
  private val directory = keysDirectoryIn(library.path())
  private val dataKeys = DataKeys(directory.resolve(DATA_KEYS_FILE))
  private val keyring = Keyring(directory, device, dataKeys, faults, passphraseCost, derivation)
  private val holder = KeysetHolder(keyring)
  private val sweep = StartSweep(dataKeys, stored, holder)
  private val foundMissing = MutableStateFlow<Set<SecretSlot>>(emptySet())

  override val state: StateFlow<VaultState> = holder.state

  override val findings: Flow<KeyFindings> = combine(dataKeys.filed, foundMissing, ::KeyFindings)

  override suspend fun prepare(opening: KeysOpening): VaultState = sweep.thenRun {
    when (opening) {
      KeysOpening.Always -> holder.withKeyset { it.state }
      KeysOpening.IfStored -> {
        val holdsADataKey = holder.withFiles { unlessFilesFail(unavailable = true) { dataKeys.holdsAny() } }
        if (holdsADataKey) holder.stateOpening(StoredKind.EveryKeyset) else state.value
      }
      KeysOpening.IfPassphraseProtected -> holder.stateOpening(setOf(StoredKind.Passphrase))
    }
  }

  override fun deferUnlock() = holder.deferUnlock()

  override suspend fun seal(slot: SecretSlot, value: String): SealResult = sweep.thenRun {
    holder.withAead(noKeychain = { SealResult.NeedsPassphrase }, blocked = SealResult::Blocked) { aead ->
      unlessFilesFail(SealResult.Blocked(KeysBlocked.Unavailable)) {
        val dataKey = dataKeys.findOrMake(slot.connectionId, aead)
        sweep.giveUpForThisRun()
        SealResult.Sealed(dataKey.sealSecret(slot, value)).also { foundMissing.update { it - slot } }
      }
    }
  }

  override suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult = sweep.thenRun {
    holder.withAead(noKeychain = { OpenResult.Missing }, blocked = OpenResult::Blocked) { aead ->
      unlessFilesFail(OpenResult.Blocked(KeysBlocked.Unavailable)) {
        val opened = dataKeys.find(slot.connectionId, aead)?.openSecret(slot, secret) ?: OpenResult.Missing
        opened.also { result -> foundMissing.update { if (result == OpenResult.Missing) it + slot else it - slot } }
      }
    }
  }

  override suspend fun erase(connectionId: Uuid): KeyFileResult = sweep.thenRun {
    holder.withFiles {
      faults.reached(KeyWritePoint.BeforeDataKeyRemoved)
      unlessFilesFail(KeyFileResult.Unavailable) {
        dataKeys.erase(connectionId)
        KeyFileResult.Done
      }
    }
  }

  override suspend fun setPassphrase(passphrase: CharArray): PassphraseResult = clearing(passphrase) {
    holder.rewriting(applies = { it == Keyset.NoKeychain }) {
      holder.keyset = keyring.writePassphraseKeyset(Keysets.newAes256Gcm(), passphrase)
      PassphraseResult.Accepted
    }
  }

  override suspend fun unlock(passphrase: CharArray): PassphraseResult = clearing(passphrase) {
    holder.withKeyset { keyset ->
      if (keyset !is Keyset.Locked) return@withKeyset PassphraseResult.NotAvailable
      unlessFilesFail(PassphraseResult.NotAvailable) {
        val opened = keyset.stored.open(passphrase, derivation) ?: return@withKeyset PassphraseResult.WrongPassphrase
        holder.keyset = Keyset.PassphraseUnlocked(Keysets.aead(opened))
        PassphraseResult.Accepted
      }
    }
  }

  override suspend fun changePassphrase(current: CharArray, new: CharArray): PassphraseResult =
    clearing(current, new) {
      holder.rewriting(applies = { it is Keyset.Locked || it is Keyset.PassphraseUnlocked }) {
        val stored = keyring.storedPassphraseKeyset() as? StoredKeyset.Passphrase
          ?: return@rewriting PassphraseResult.NotAvailable
        val opened = stored.open(current, derivation) ?: return@rewriting PassphraseResult.WrongPassphrase
        unlessFilesFail(PassphraseResult.NotAvailable) {
          holder.keyset = keyring.writePassphraseKeyset(opened, new)
          PassphraseResult.Accepted
        }
      }
    }

  override suspend fun reset(newPassphrase: CharArray): ErasingResult = clearing(newPassphrase) {
    holder.erasing { current ->
      when (current) {
        is Keyset.Locked, Keyset.PassphraseDamaged -> keyring.resetPassphraseKeyset(newPassphrase)
        Keyset.Closed, is Keyset.DeviceUnlocked, is Keyset.PassphraseUnlocked, Keyset.NoKeychain, Keyset.Unavailable,
        is Keyset.DeviceDamaged, is Keyset.DeviceKeyNotFound -> null
      }
    }
  }

  override suspend fun replaceDeviceKeyset(): ErasingResult = holder.erasing { current ->
    when (current) {
      is Keyset.DeviceDamaged -> keyring.replaceDeviceKeyset(current.keyName)
      is Keyset.DeviceKeyNotFound -> keyring.replaceDeviceKeyset(current.keyName)
      Keyset.Closed, is Keyset.DeviceUnlocked, is Keyset.PassphraseUnlocked, is Keyset.Locked, Keyset.NoKeychain,
      Keyset.Unavailable, Keyset.PassphraseDamaged -> null
    }
  }
}

private suspend inline fun <T> KeysetHolder.withAead(
  crossinline noKeychain: () -> T,
  crossinline blocked: (KeysBlocked) -> T,
  crossinline use: (Aead) -> T,
): T = withKeyset { current ->
  when (current) {
    is Keyset.DeviceUnlocked -> use(current.aead)
    is Keyset.PassphraseUnlocked -> use(current.aead)
    Keyset.NoKeychain -> noKeychain()
    is Keyset.Locked, Keyset.PassphraseDamaged -> blocked(KeysBlocked.Locked)
    is Keyset.DeviceDamaged, is Keyset.DeviceKeyNotFound -> blocked(KeysBlocked.Unreadable)
    Keyset.Closed, Keyset.Unavailable -> blocked(if (readsAsLocked) KeysBlocked.Locked else KeysBlocked.Unavailable)
  }
}

private inline fun <T> unlessFilesFail(unavailable: T, work: () -> T): T = try {
  work()
} catch (_: IOException) {
  unavailable
}

private inline fun <T> clearing(vararg passphrases: CharArray, action: () -> T): T = try {
  action()
} finally {
  passphrases.forEach { it.fill(Char(0)) }
}

private fun Aead.sealSecret(slot: SecretSlot, value: String): SealedSecret {
  val plain = value.encodeToByteArray()
  return try {
    SealedSecret(encrypt(plain, slot.associatedData()))
  } finally {
    plain.fill(0)
  }
}

private fun Aead.openSecret(slot: SecretSlot, secret: SealedSecret): OpenResult {
  val plain = try {
    decrypt(secret.bytes, slot.associatedData())
  } catch (_: GeneralSecurityException) {
    return OpenResult.Missing
  }
  return try {
    OpenResult.Opened(plain.decodeToString())
  } finally {
    plain.fill(0)
  }
}

private fun SecretSlot.associatedData(): ByteArray = when (this) {
  is SecretSlot.ApiKey -> byteArrayOf(API_KEY_TAG) + connectionId.toByteArray()
  is SecretSlot.HeaderValue ->
    byteArrayOf(HEADER_VALUE_TAG) + connectionId.toByteArray() + name.canonical.encodeToByteArray()
}

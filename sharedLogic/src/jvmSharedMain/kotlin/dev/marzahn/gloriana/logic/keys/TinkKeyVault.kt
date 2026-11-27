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
import java.security.GeneralSecurityException
import kotlin.uuid.Uuid

private const val KEYS_DIRECTORY = "keys"
private const val DATA_KEYS_FILE = "connections.json"
private const val API_KEY_TAG: Byte = 1
private const val HEADER_VALUE_TAG: Byte = 2

internal class TinkKeyVault(
  library: LibraryDirectory,
  device: DeviceKeyStore,
  private val faults: Faults,
  stored: StoredConnections,
  passphraseCost: Argon2idCost = Argon2idCost.Default,
) : KeyVault {
  private val directory = library.path().resolve(KEYS_DIRECTORY)
  private val dataKeys = DataKeys(directory.resolve(DATA_KEYS_FILE))
  private val keyring = Keyring(directory, device, dataKeys, faults, passphraseCost)
  private val holder = KeysetHolder(keyring)
  private val sweep = StartSweep(dataKeys, stored)
  private val foundMissing = MutableStateFlow<Set<SecretSlot>>(emptySet())

  override val state: StateFlow<VaultState> = holder.state

  override val findings: Flow<KeyFindings> = combine(dataKeys.filed, foundMissing, ::KeyFindings)

  override suspend fun prepare(): VaultState = swept { holder.withKeyset { it.state } }

  override suspend fun prepareIfPassphraseProtected(): VaultState = swept {
    when (holder.withFiles { keyring.storedKind() }) {
      StoredKind.Passphrase -> holder.withKeyset { it.state }
      StoredKind.Unreadable -> VaultState.Unavailable
      StoredKind.Device, StoredKind.None -> state.value
    }
  }

  override suspend fun seal(slot: SecretSlot, value: String): SealResult = swept {
    holder.withKeyset { keyset ->
      keyset.withAead(SealOutcomes) { aead ->
        unlessFilesFail(SealResult.Unavailable) {
          val dataKey = dataKeys.findOrMake(slot.connectionId, aead)
          sweep.giveUpForThisRun()
          SealResult.Sealed(dataKey.sealSecret(slot, value)).also { foundMissing.update { it - slot } }
        }
      }
    }
  }

  override suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult = swept {
    holder.withKeyset { keyset ->
      keyset.withAead(OpenOutcomes) { aead ->
        unlessFilesFail(OpenResult.Unavailable) {
          val opened = dataKeys.find(slot.connectionId, aead)?.openSecret(slot, secret) ?: OpenResult.Missing
          opened.also { result -> foundMissing.update { if (result == OpenResult.Missing) it + slot else it - slot } }
        }
      }
    }
  }

  private suspend fun <T> swept(action: suspend () -> T): T {
    sweep.runIfDue(holder)
    return action()
  }

  override suspend fun erase(connectionId: Uuid): KeyFileResult = swept {
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
      val opened = keyset.stored.open(passphrase) ?: return@withKeyset PassphraseResult.WrongPassphrase
      holder.keyset = Keyset.PassphraseUnlocked(Keysets.aead(opened))
      PassphraseResult.Accepted
    }
  }

  override suspend fun changePassphrase(current: CharArray, new: CharArray): PassphraseResult =
    clearing(current, new) {
      holder.rewriting(applies = { it is Keyset.Locked || it is Keyset.PassphraseUnlocked }) {
        val stored = keyring.storedPassphraseKeyset() as? StoredKeyset.Passphrase
          ?: return@rewriting PassphraseResult.NotAvailable
        val opened = stored.open(current) ?: return@rewriting PassphraseResult.WrongPassphrase
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

private class WithoutAead<T>(val needsPassphrase: T, val locked: T, val unreadable: T, val unavailable: T)

private val SealOutcomes = WithoutAead<SealResult>(
  needsPassphrase = SealResult.NeedsPassphrase,
  locked = SealResult.Locked,
  unreadable = SealResult.Unreadable,
  unavailable = SealResult.Unavailable,
)

private val OpenOutcomes = WithoutAead<OpenResult>(
  needsPassphrase = OpenResult.Missing,
  locked = OpenResult.Locked,
  unreadable = OpenResult.Unreadable,
  unavailable = OpenResult.Unavailable,
)

private inline fun <T> Keyset.withAead(without: WithoutAead<T>, use: (Aead) -> T): T = when (this) {
  is Keyset.DeviceUnlocked -> use(aead)
  is Keyset.PassphraseUnlocked -> use(aead)
  Keyset.NoKeychain -> without.needsPassphrase
  is Keyset.Locked -> without.locked
  Keyset.PassphraseDamaged, is Keyset.DeviceDamaged, is Keyset.DeviceKeyNotFound -> without.unreadable
  Keyset.Closed, Keyset.Unavailable -> without.unavailable
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

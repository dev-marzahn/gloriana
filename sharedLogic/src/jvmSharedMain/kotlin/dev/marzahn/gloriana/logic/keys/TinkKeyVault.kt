package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.Aead
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.Faults
import kotlinx.coroutines.flow.StateFlow
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

  override val state: StateFlow<VaultState> = holder.state

  override suspend fun prepare(): VaultState = swept { holder.withKeyset { it.state } }

  suspend fun sweepAtStart() = sweep.runIfDue(holder)

  override suspend fun seal(slot: SecretSlot, value: String): SealResult = swept {
    holder.withKeyset { keyset ->
      when (val aead = keyset.aead()) {
        null -> if (keyset.asksForPassphrase()) SealResult.Locked else SealResult.Unavailable
        else -> unlessFilesFail(SealResult.Unavailable) {
          val dataKey = dataKeys.findOrMake(slot.connectionId, aead)
          sweep.giveUpForThisRun()
          SealResult.Sealed(dataKey.sealSecret(slot, value))
        }
      }
    }
  }

  override suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult = swept {
    holder.withKeyset { keyset ->
      when (val aead = keyset.aead()) {
        null -> if (keyset.asksForPassphrase()) OpenResult.Locked else OpenResult.Unavailable
        else -> unlessFilesFail(OpenResult.Unavailable) {
          dataKeys.find(slot.connectionId, aead)?.openSecret(slot, secret) ?: OpenResult.Missing
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
        holder.keyset = keyring.writePassphraseKeyset(opened, new)
        PassphraseResult.Accepted
      }
    }

  override suspend fun reset(newPassphrase: CharArray): PassphraseResult = clearing(newPassphrase) {
    holder.rewriting(applies = { it is Keyset.Locked || it == Keyset.PassphraseDamaged }) {
      dataKeys.eraseAll()
      holder.keyset = keyring.writePassphraseKeyset(Keysets.newAes256Gcm(), newPassphrase)
      PassphraseResult.Accepted
    }
  }

  override suspend fun replaceDeviceKeyset(): PassphraseResult =
    holder.rewriting(applies = { it is Keyset.DeviceDamaged || it is Keyset.DeviceKeyNotFound }) { current ->
      val replacing = when (current) {
        is Keyset.DeviceDamaged -> current.keyName
        is Keyset.DeviceKeyNotFound -> current.keyName
        else -> null
      }
      val replaced = keyring.replaceDeviceKeyset(replacing)
      if (replaced is Keyset.DeviceUnlocked) {
        holder.keyset = replaced
        PassphraseResult.Accepted
      } else {
        PassphraseResult.NotAvailable
      }
    }
}

private fun Keyset.aead(): Aead? = when (this) {
  is Keyset.DeviceUnlocked -> aead
  is Keyset.PassphraseUnlocked -> aead
  else -> null
}

private fun Keyset.asksForPassphrase(): Boolean = this is Keyset.Locked || this == Keyset.NoKeychain

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

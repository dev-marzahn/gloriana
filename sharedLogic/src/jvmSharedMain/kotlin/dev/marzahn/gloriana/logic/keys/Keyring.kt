package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.removePartialWrites
import java.io.IOException
import java.nio.file.Path

internal sealed interface Keyset {
  val state: VaultState

  data object Closed : Keyset {
    override val state = VaultState.Closed
  }

  class DeviceUnlocked(val aead: Aead) : Keyset {
    override val state = VaultState.Unlocked
  }

  class PassphraseUnlocked(val aead: Aead) : Keyset {
    override val state = VaultState.PassphraseUnlocked
  }

  class Locked(val stored: StoredKeyset.Passphrase) : Keyset {
    override val state = VaultState.Locked
  }

  data object NoKeychain : Keyset {
    override val state = VaultState.NoKeychain
  }

  data object Unavailable : Keyset {
    override val state = VaultState.Unavailable
  }

  data object PassphraseDamaged : Keyset {
    override val state = VaultState.PassphraseDamaged
  }

  class DeviceDamaged(val keyName: String?) : Keyset {
    override val state = VaultState.DeviceDamaged
  }

  class DeviceKeyNotFound(val keyName: String) : Keyset {
    override val state = VaultState.DeviceKeyNotFound
  }
}

internal class Keyring(
  private val directory: Path,
  private val device: DeviceKeyStore,
  private val dataKeys: DataKeys,
  private val faults: Faults,
  private val passphraseCost: Argon2idCost,
) {
  private val files = KeysetFiles(directory)

  fun open(): Keyset = try {
    directory.removePartialWrites()
    when (val stored = files.read()) {
      is StoredKeyset.Passphrase -> Keyset.Locked(stored)
      is StoredKeyset.Device -> openDeviceKeyset(stored)
      is StoredKeyset.Damaged -> when (stored.kind) {
        KeysetKind.Passphrase -> Keyset.PassphraseDamaged
        KeysetKind.Device -> Keyset.DeviceDamaged(keyName = null)
      }
      null -> newDeviceKeyset(replacing = null, noStore = Keyset.NoKeychain)
    }
  } catch (_: IOException) {
    Keyset.Unavailable
  }

  fun storedPassphraseKeyset(): StoredKeyset? = files.read()

  fun writePassphraseKeyset(keyset: KeysetHandle, passphrase: CharArray): Keyset {
    val protection = newPassphraseProtection(passphraseCost)
    files.write(protection, keyset, protection.wrapping(passphrase)) { faults.reached(KeyWritePoint.NewKeysetStaged) }
    return Keyset.PassphraseUnlocked(Keysets.aead(keyset))
  }

  fun replaceDeviceKeyset(replacing: String?): Keyset = newDeviceKeyset(replacing, noStore = Keyset.Unavailable)

  private fun openDeviceKeyset(stored: StoredKeyset.Device): Keyset = when (val key = device.find(stored.keyName)) {
    is DeviceKey.Found -> when (val opening = stored.open(key.key)) {
      is KeysetOpening.Opened -> Keyset.DeviceUnlocked(Keysets.aead(opening.keyset))
      KeysetOpening.WrongKey -> Keyset.DeviceDamaged(stored.keyName)
      KeysetOpening.Lost -> replaceDeviceKeyset(stored.keyName)
    }
    DeviceKey.Lost -> replaceDeviceKeyset(stored.keyName)
    DeviceKey.Unusable -> Keyset.DeviceDamaged(stored.keyName)
    DeviceKey.Unconfirmed -> Keyset.DeviceKeyNotFound(stored.keyName)
    DeviceKey.Unreachable, DeviceKey.NoStore -> Keyset.Unavailable
  }

  private fun newDeviceKeyset(replacing: String?, noStore: Keyset): Keyset = when (val created = device.create()) {
    is NewDeviceKey.Made -> {
      dataKeys.eraseAll()
      val keyset = Keysets.newAes256Gcm()
      files.write(KeysetProtection.Device(created.name), keyset, created.key) {
        faults.reached(KeyWritePoint.NewKeysetStaged)
      }
      replacing?.let(device::delete)
      Keyset.DeviceUnlocked(Keysets.aead(keyset))
    }
    NewDeviceKey.NoStore -> noStore
    NewDeviceKey.Unreachable -> Keyset.Unavailable
  }
}

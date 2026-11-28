package dev.marzahn.gloriana.logic.keys

private const val KEY_NAME_PREFIX = "keyset-"
private const val KEY_NAME_RANDOM_BYTES = 8

sealed interface KeychainSecret {
  class Found(val bytes: ByteArray) : KeychainSecret

  data class NotFound(val confirmed: Boolean) : KeychainSecret

  data object Malformed : KeychainSecret

  data object Unreachable : KeychainSecret

  data object NoKeychain : KeychainSecret
}

enum class KeychainWrite {
  Written,
  Unreachable,
  NoKeychain,
}

interface OsKeychain {
  fun read(account: String): KeychainSecret

  fun write(account: String, secret: ByteArray): KeychainWrite

  fun delete(account: String)
}

class KeychainKeyStore(private val keychain: OsKeychain) : DeviceKeyStore {
  override fun find(name: String): DeviceKey = when (val secret = keychain.read(name)) {
    is KeychainSecret.Found -> found(secret.bytes)
    is KeychainSecret.NotFound -> if (secret.confirmed) DeviceKey.Lost else DeviceKey.Unconfirmed
    KeychainSecret.Malformed -> DeviceKey.Unusable
    KeychainSecret.Unreachable -> DeviceKey.Unreachable
    KeychainSecret.NoKeychain -> DeviceKey.NoStore
  }

  override fun create(): NewDeviceKey {
    val name = KEY_NAME_PREFIX + Keysets.randomBytes(KEY_NAME_RANDOM_BYTES).toHexString()
    val secret = Keysets.randomBytes(AES_256_KEY_BYTES)
    return try {
      when (keychain.write(name, secret)) {
        KeychainWrite.Written -> readBack(name, secret)
        KeychainWrite.Unreachable -> NewDeviceKey.Unreachable
        KeychainWrite.NoKeychain -> NewDeviceKey.NoStore
      }
    } finally {
      secret.fill(0)
    }
  }

  override fun delete(name: String) = keychain.delete(name)

  private fun readBack(name: String, written: ByteArray): NewDeviceKey {
    val stored = keychain.read(name)
    val key = if (stored is KeychainSecret.Found && stored.bytes.contentEquals(written)) found(stored.bytes) else null
    return if (key is DeviceKey.Found) NewDeviceKey.Made(name, key.key) else NewDeviceKey.Unreachable
  }

  private fun found(secret: ByteArray): DeviceKey {
    if (secret.size != AES_256_KEY_BYTES) return DeviceKey.Unusable
    return try {
      DeviceKey.Found(AesGcmWrappingKey(secret))
    } finally {
      secret.fill(0)
    }
  }
}

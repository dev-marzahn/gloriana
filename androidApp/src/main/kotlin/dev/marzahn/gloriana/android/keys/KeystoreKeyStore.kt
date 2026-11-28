package dev.marzahn.gloriana.android.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import dev.marzahn.gloriana.logic.keys.DeviceKey
import dev.marzahn.gloriana.logic.keys.DeviceKeyStore
import dev.marzahn.gloriana.logic.keys.DeviceKeyUnreachable
import dev.marzahn.gloriana.logic.keys.NewDeviceKey
import dev.marzahn.gloriana.logic.keys.Unwrapped
import dev.marzahn.gloriana.logic.keys.WrappingKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val APP_KEYSTORE_ALIAS_PREFIX = "gloriana_keyset_"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_BITS = 256
private const val IV_BYTES = 12
private const val TAG_BITS = 128
private const val SELF_TEST_BYTES = 32
private const val ALIAS_RANDOM_BYTES = 8

interface KeystoreKeys {
  fun load(alias: String): SecretKey?

  fun generate(alias: String): SecretKey

  fun delete(alias: String)
}

fun interface KeystoreCiphers {
  fun cipher(mode: Int, key: SecretKey, iv: GCMParameterSpec?): Cipher

  companion object {
    val Default = KeystoreCiphers { mode, key, iv ->
      Cipher.getInstance(TRANSFORMATION).apply { if (iv == null) init(mode, key) else init(mode, key, iv) }
    }
  }
}

class AndroidKeystoreKeys : KeystoreKeys {
  override fun load(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

  override fun generate(alias: String): SecretKey =
    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
      init(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(KEY_BITS)
          .build(),
      )
    }.generateKey()

  override fun delete(alias: String) = keyStore().deleteEntry(alias)

  private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
}

class KeystoreKeyStore(
  private val keys: KeystoreKeys,
  private val ciphers: KeystoreCiphers = KeystoreCiphers.Default,
) : DeviceKeyStore {
  override fun find(name: String): DeviceKey = keystoreVerdict(lost = { DeviceKey.Lost }, { DeviceKey.Unreachable }) {
    keys.load(name)?.let { DeviceKey.Found(KeystoreWrappingKey(it, ciphers)) } ?: DeviceKey.Lost
  }

  override fun create(): NewDeviceKey {
    val name = APP_KEYSTORE_ALIAS_PREFIX + ByteArray(ALIAS_RANDOM_BYTES).also(SecureRandom()::nextBytes).toHexString()
    val generated = generate(name) ?: return NewDeviceKey.Unreachable
    return when {
      roundTrips(generated) -> NewDeviceKey.Made(name, generated)
      else -> NewDeviceKey.NoStore.also { delete(name) }
    }
  }

  override fun delete(name: String) {
    keystoreVerdict(lost = { }, unreachable = { }) { keys.delete(name) }
  }

  private fun generate(name: String): WrappingKey? = keystoreVerdict(lost = { null }, unreachable = { null }) {
    KeystoreWrappingKey(keys.generate(name), ciphers)
  }

  private fun roundTrips(wrapping: WrappingKey): Boolean {
    val probe = ByteArray(SELF_TEST_BYTES).also(SecureRandom()::nextBytes)
    val opened = try {
      wrapping.open(wrapping.seal(probe, probe), probe)
    } catch (_: DeviceKeyUnreachable) {
      return false
    }
    return opened is Unwrapped.Opened && opened.bytes.contentEquals(probe)
  }
}

class KeystoreWrappingKey(private val key: SecretKey, private val ciphers: KeystoreCiphers) : WrappingKey {
  override fun seal(plain: ByteArray, associatedData: ByteArray): ByteArray =
    keystoreVerdict(lost = { throw DeviceKeyUnreachable(it) }, unreachable = { throw DeviceKeyUnreachable(it) }) {
      val cipher = ciphers.cipher(Cipher.ENCRYPT_MODE, key, iv = null).apply { updateAAD(associatedData) }
      cipher.iv + cipher.doFinal(plain)
    }

  override fun open(sealed: ByteArray, associatedData: ByteArray): Unwrapped {
    if (sealed.size <= IV_BYTES) return Unwrapped.WrongKey
    return keystoreVerdict(lost = { Unwrapped.Lost }, unreachable = { throw DeviceKeyUnreachable(it) }) {
      try {
        val cipher = ciphers.cipher(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        cipher.updateAAD(associatedData)
        Unwrapped.Opened(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
      } catch (_: AEADBadTagException) {
        Unwrapped.WrongKey
      }
    }
  }
}

private inline fun <T> keystoreVerdict(lost: (Exception) -> T, unreachable: (Exception) -> T, operation: () -> T): T =
  try {
    operation()
  } catch (failure: KeyPermanentlyInvalidatedException) {
    lost(failure)
  } catch (failure: GeneralSecurityException) {
    unreachable(failure)
  } catch (failure: IOException) {
    unreachable(failure)
  } catch (failure: ProviderException) {
    unreachable(failure)
  }

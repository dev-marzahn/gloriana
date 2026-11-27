package dev.marzahn.gloriana.android.keys

import dev.marzahn.gloriana.logic.keys.DeviceKey
import dev.marzahn.gloriana.logic.keys.DeviceKeyUnreachable
import dev.marzahn.gloriana.logic.keys.NewDeviceKey
import dev.marzahn.gloriana.logic.keys.Unwrapped
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SoftwareKeys : KeystoreKeys {
  val keys = mutableMapOf<String, SecretKey>()
  var loadFailure: Exception? = null
  var generateFailure: Exception? = null
  val deleted = mutableListOf<String>()

  override fun load(alias: String): SecretKey? {
    loadFailure?.let { throw it }
    return keys[alias]
  }

  override fun generate(alias: String): SecretKey {
    generateFailure?.let { throw it }
    return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { keys[alias] = it }
  }

  override fun delete(alias: String) {
    deleted += alias
    keys -= alias
  }
}

val NoEncryption = KeystoreCiphers { mode, key, iv ->
  if (mode == Cipher.ENCRYPT_MODE) throw InvalidKeyException("cannot encrypt")
  KeystoreCiphers.Default.cipher(mode, key, iv)
}

class KeystoreKeyStoreTest {
  private val keys = SoftwareKeys()
  private val store = KeystoreKeyStore(keys)
  private val associated = "header".encodeToByteArray()

  @Test
  fun aCreatedKeyIsFoundByItsNameAndOpensWhatItSealed() {
    val created = assertIs<NewDeviceKey.Made>(store.create())
    val sealed = created.key.seal("keyset".encodeToByteArray(), associated)

    val opened = assertIs<DeviceKey.Found>(store.find(created.name)).key.open(sealed, associated)
    assertContentEquals("keyset".encodeToByteArray(), assertIs<Unwrapped.Opened>(opened).bytes)
  }

  @Test
  fun eachKeyGetsItsOwnName() {
    val first = assertIs<NewDeviceKey.Made>(store.create()).name
    val second = assertIs<NewDeviceKey.Made>(store.create()).name

    assertTrue(first != second && first.startsWith(APP_KEYSTORE_ALIAS_PREFIX))
    assertEquals(setOf(first, second), keys.keys.keys)
  }

  @Test
  fun noKeyOfThatNameIsLost() {
    assertEquals(DeviceKey.Lost, store.find("gloriana_keyset_missing"))
  }

  @Test
  fun anUnrecoverableKeyIsUnreachableNotLost() {
    keys.loadFailure = UnrecoverableKeyException("Failed to obtain information about key")

    assertEquals(DeviceKey.Unreachable, store.find("any"))
  }

  @Test
  fun anUnrecoverableKeyWhileOpeningIsUnreachableNotLost() {
    val sealed = assertIs<NewDeviceKey.Made>(store.create()).key.seal("keyset".encodeToByteArray(), associated)
    val failing = KeystoreWrappingKey(keys.keys.values.single()) { _, _, _ ->
      throw UnrecoverableKeyException("Failed to obtain information about key")
    }

    assertFailsWith<DeviceKeyUnreachable> { failing.open(sealed, associated) }
  }

  @Test
  fun aKeystoreThatCannotBeReadIsUnreachable() {
    keys.loadFailure = KeyStoreException("busy")

    assertEquals(DeviceKey.Unreachable, store.find("any"))
  }

  @Test
  fun aKeyThatCannotEncryptFailsTheRoundTripAndMeansNoKeystore() {
    val failing = KeystoreKeyStore(keys, NoEncryption)

    assertEquals(NewDeviceKey.NoStore, failing.create())
    assertEquals(1, keys.deleted.size)
    assertTrue(keys.keys.isEmpty())
  }

  @Test
  fun aGenerateThatThrowsIsUnreachableNotNoKeystore() {
    keys.generateFailure = ProviderException("busy")

    assertEquals(NewDeviceKey.Unreachable, store.create())
  }

  @Test
  fun anotherKeyIsTheWrongKeyNotALostOne() {
    val sealed = assertIs<NewDeviceKey.Made>(store.create()).key.seal("keyset".encodeToByteArray(), associated)
    val other = assertIs<NewDeviceKey.Made>(store.create()).key

    assertEquals(Unwrapped.WrongKey, other.open(sealed, associated))
    assertEquals(Unwrapped.WrongKey, other.open(byteArrayOf(1, 2, 3), associated))
  }

  @Test
  fun aKeyThatFailsMidUseIsUnreachable() {
    val wrapping = KeystoreWrappingKey(KeyGenerator.getInstance("AES").generateKey(), NoEncryption)

    assertFailsWith<DeviceKeyUnreachable> { wrapping.seal(byteArrayOf(1), associated) }
  }
}

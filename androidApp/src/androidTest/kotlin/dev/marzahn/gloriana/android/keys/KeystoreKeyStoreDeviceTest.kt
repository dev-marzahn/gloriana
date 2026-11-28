package dev.marzahn.gloriana.android.keys

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.platform.app.InstrumentationRegistry
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.keys.DeviceKeyStore
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.keys.SealResult
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.logic.keys.VaultState
import dev.marzahn.gloriana.logic.keys.keyVault
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class KeystoreKeyStoreDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val library = File(context.cacheDir, "keystore-test-library").toPath()
  private val keystore = KeystoreKeyStore(AndroidKeystoreKeys())
  private val connection = Uuid.random()

  @After
  fun deleteTestKeys() {
    appAliases().forEach(AndroidKeystoreKeys()::delete)
    library.toFile().deleteRecursively()
  }

  @Test
  fun theKeysetIsWrappedByTheKeystoreAndOpensAfterARestart() = runBlocking {
    val sealed = sealed(vault(keystore))

    val opened = vault(keystore).open(SecretSlot.ApiKey(connection), sealed)

    assertEquals("sk-device", assertIs<OpenResult.Opened>(opened).value)
  }

  @Test
  fun aRemovedKeystoreKeyIsALostWrappingKey() = runBlocking {
    val sealed = sealed(vault(keystore))
    appAliases().forEach(AndroidKeystoreKeys()::delete)

    val restarted = vault(keystore)
    assertEquals(OpenResult.Missing, restarted.open(SecretSlot.ApiKey(connection), sealed))
    assertEquals(VaultState.Unlocked, restarted.state.value)
  }

  @Test
  fun anUnrecoverableKeyLeavesEverythingAndTheNextUseOpensIt() = runBlocking {
    val sealed = sealed(vault(keystore))
    val aliases = appAliases()

    val restarted = vault(KeystoreKeyStore(UnrecoverableOnce()))
    assertEquals(OpenResult.Unavailable, restarted.open(SecretSlot.ApiKey(connection), sealed))
    assertEquals(VaultState.Unavailable, restarted.state.value)
    assertEquals(aliases, appAliases())

    val retried = restarted.open(SecretSlot.ApiKey(connection), sealed)
    assertEquals("sk-device", assertIs<OpenResult.Opened>(retried).value)
  }

  @Test
  fun aKeyInvalidatedWhenItOpensTheKeysetIsReplacedAndTheNewOneOpensNextStart() = runBlocking {
    val old = sealed(vault(keystore))

    val restarted = vault(KeystoreKeyStore(AndroidKeystoreKeys(), InvalidatedOnDecryptOnce()))
    assertEquals(OpenResult.Missing, restarted.open(SecretSlot.ApiKey(connection), old))
    val renewed = sealed(restarted)

    val next = vault(keystore).open(SecretSlot.ApiKey(connection), renewed)
    assertEquals("sk-device", assertIs<OpenResult.Opened>(next).value)
    assertEquals(1, appAliases().size)
  }

  @Test
  fun aKeystoreThatCannotEncryptLeavesNoUnwrappedKeysetAndAsksForAPassphrase() = runBlocking {
    val broken = KeystoreKeyStore(AndroidKeystoreKeys(), CannotEncrypt)
    val fallback = vault(broken)

    assertEquals(VaultState.NoKeychain, fallback.prepare())
    assertEquals(SealResult.NeedsPassphrase, fallback.seal(SecretSlot.ApiKey(connection), "sk-device"))
    assertEquals(emptyList(), appAliases())
    assertEquals(emptyList(), keyFiles())

    assertEquals(PassphraseResult.Accepted, fallback.setPassphrase("correct horse".toCharArray()))
    sealed(fallback)
    assertEquals(listOf("connections.json", "keyset-passphrase.json"), keyFiles())
    assertEquals(emptyList(), appAliases())
    assertEquals(VaultState.Locked, vault(broken).prepare())
  }

  private fun keyFiles(): List<String> =
    library.resolve("keys").toFile().list().orEmpty().filterNot { it.startsWith(".") }.sorted()

  private suspend fun sealed(vault: dev.marzahn.gloriana.logic.keys.KeyVault) =
    assertIs<SealResult.Sealed>(vault.seal(SecretSlot.ApiKey(connection), "sk-device")).secret

  private fun vault(device: DeviceKeyStore) =
    keyVault(LibraryDirectory { library }, device, Faults.None, stored = { setOf(connection) })

  private fun appAliases(): List<String> = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    .aliases().toList().filter { it.startsWith(APP_KEYSTORE_ALIAS_PREFIX) }

  private class UnrecoverableOnce : KeystoreKeys by AndroidKeystoreKeys() {
    private var thrown = false

    override fun load(alias: String): SecretKey? {
      if (!thrown) {
        thrown = true
        throw UnrecoverableKeyException("invalidated")
      }
      return AndroidKeystoreKeys().load(alias)
    }
  }

  private class InvalidatedOnDecryptOnce : KeystoreCiphers {
    private var thrown = false

    override fun cipher(mode: Int, key: SecretKey, iv: javax.crypto.spec.GCMParameterSpec?): Cipher {
      if (mode == Cipher.DECRYPT_MODE && !thrown) {
        thrown = true
        throw KeyPermanentlyInvalidatedException("lock screen removed")
      }
      return KeystoreCiphers.Default.cipher(mode, key, iv)
    }
  }

  private object CannotEncrypt : KeystoreCiphers {
    override fun cipher(mode: Int, key: SecretKey, iv: javax.crypto.spec.GCMParameterSpec?): Cipher {
      if (mode == Cipher.ENCRYPT_MODE) throw InvalidKeyException("cannot encrypt")
      return KeystoreCiphers.Default.cipher(mode, key, iv)
    }
  }
}

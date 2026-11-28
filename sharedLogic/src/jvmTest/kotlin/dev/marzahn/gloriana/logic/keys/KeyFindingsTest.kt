package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.CustomHeader
import dev.marzahn.gloriana.logic.connection.secretChecks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val CHECK_WAIT = 1_000L

class KeyFindingsTest {
  @TempDir
  lateinit var library: Path

  private val vaults by lazy { Vaults(library) }
  private val connection = Uuid.random()
  private val other = Uuid.random()
  private val workFields =
    ConnectionFields(name = "Work", format = ApiFormat.AnthropicMessages, baseUrl = "https://a.test", model = "m")

  private suspend fun KeyVault.missing(slot: SecretSlot) = findings.first().isMissing(slot)

  private suspend fun KeyVault.checked(saved: Connection) {
    secretChecks(flowOf(listOf(saved))).first()
  }

  @Test
  fun onAKeychainDeviceTheOldKeyIsMissingAfterStartOverAPartialReEntryAndARestart() = runTest {
    val oldKey = vaults.start().sealed(apiKey(connection), "sk-before")
    vaults.keychain.replaceEverySecret(ByteArray(32) { 7 })
    val startingOver = vaults.start()
    assertEquals(VaultState.DeviceDamaged, startingOver.prepare())
    assertEquals(ErasingResult.Done, startingOver.replaceDeviceKeyset())
    val newHeader = startingOver.sealed(header(connection, "X-Org"), "org-after")
    val saved = Connection(
      id = connection,
      fields = workFields,
      key = oldKey,
      headers = listOf(CustomHeader("X-Org", newHeader)),
    )

    val restarted = vaults.start()
    val checking = launch { restarted.secretChecks(flowOf(listOf(saved))).first() }
    restarted.prepare()
    checking.join()

    assertTrue(restarted.missing(apiKey(connection)))
    assertFalse(restarted.missing(header(connection, "X-Org")))
  }

  @Test
  fun nothingIsMissingBeforeTheVaultHasReadItsDataKeys() = runTest {
    assertFalse(vaults.start().missing(apiKey(connection)))
  }

  @Test
  fun aSealedValueIsNotMissing() = runTest {
    val vault = vaults.start()
    vault.sealed(apiKey(connection), "sk-kept")

    assertFalse(vault.missing(apiKey(connection)))
  }

  @Test
  fun aConnectionWithNoDataKeyAtStartIsMissingItsValues() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")

    val restarted = vaults.start(stored = { setOf(connection, other) })
    restarted.prepare(KeysOpening.IfPassphraseProtected)

    assertFalse(restarted.missing(apiKey(connection)))
    assertTrue(restarted.missing(apiKey(other)))
    assertTrue(restarted.missing(header(other, "X-Org")))
  }

  @Test
  fun anErasedDataKeyMakesItsValuesMissing() = runTest {
    val vault = vaults.start()
    vault.sealed(apiKey(connection), "sk-gone")

    assertEquals(KeyFileResult.Done, vault.erase(connection))

    assertTrue(vault.missing(apiKey(connection)))
  }

  @Test
  fun aValueThatFailsToOpenIsMissingUntilSealedAgain() = runTest {
    val vault = vaults.start()
    val key = vault.sealed(apiKey(connection), "sk-moved")
    val moved = SealedSecret(key.bytes.copyOf().also { it[it.lastIndex] = it.last().inc() })

    assertEquals(OpenResult.Missing, vault.open(apiKey(connection), moved))
    assertTrue(vault.missing(apiKey(connection)))

    vault.sealed(apiKey(connection), "sk-new")
    assertFalse(vault.missing(apiKey(connection)))
  }

  @Test
  fun afterAResetAndAPartialReEntryTheOldKeyIsMissingAfterARestart() = runTest {
    vaults.keychain.noKeychain = true
    val first = vaults.start()
    first.setPassphrase("correct horse".passphrase())
    val oldKey = first.sealed(apiKey(connection), "sk-before")
    val oldHeader = first.sealed(header(connection, "X-Org"), "org-before")

    val resetting = vaults.start()
    assertEquals(VaultState.Locked, resetting.prepare())
    assertEquals(ErasingResult.Done, resetting.reset("battery staple".passphrase()))
    val newHeader = resetting.sealed(header(connection, "X-Org"), "org-after")

    val restarted = vaults.start()
    assertEquals(PassphraseResult.Accepted, restarted.unlock("battery staple".passphrase()))
    val saved = Connection(
      id = connection,
      fields = workFields,
      key = oldKey,
      headers = listOf(CustomHeader("X-Org", newHeader)),
    )
    restarted.checked(saved)

    assertTrue(restarted.missing(apiKey(connection)))
    assertFalse(restarted.missing(header(connection, "X-Org")))
    assertEquals(OpenResult.Missing, restarted.open(header(connection, "X-Org"), oldHeader))
  }

  @Test
  fun checkingSecretsDoesNothingWhileTheKeysetIsClosed() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-kept")
    val readsBefore = vaults.keychain.reads
    val restarted = vaults.start()
    val saved = Connection(
      id = connection,
      fields = workFields,
      key = key,
      headers = emptyList(),
    )

    withTimeoutOrNull(CHECK_WAIT) { restarted.checked(saved) }

    assertEquals(readsBefore, vaults.keychain.reads)
  }

  @Test
  fun aDeviceKeysetIsNotOpenedByTheLaunchCheck() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-device")
    val readsBefore = vaults.keychain.reads

    val restarted = vaults.start()

    assertEquals(VaultState.Closed, restarted.prepare(KeysOpening.IfPassphraseProtected))
    assertEquals(readsBefore, vaults.keychain.reads)
  }

  @Test
  fun withNoKeysetOpeningAStoredOneAsksNoKeychainAndMakesNoFile() = runTest {
    val vault = vaults.start()

    assertEquals(VaultState.Closed, vault.prepare(KeysOpening.IfStored))
    assertEquals(0, vaults.keychain.reads)
    assertTrue(vaults.keychain.secrets.isEmpty())
    assertFalse(Files.exists(library.resolve("keys/keyset-device.json")))
  }

  @Test
  fun aStoredDeviceKeysetIsOpenedByPrepareIfStored() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-device")

    assertEquals(VaultState.Unlocked, vaults.start().prepare(KeysOpening.IfStored))
  }

  @Test
  fun aStoredPassphraseKeysetIsLockedByPrepareIfStored() = runTest {
    vaults.keychain.noKeychain = true
    vaults.start().apply { setPassphrase("correct horse".passphrase()) }.sealed(apiKey(connection), "sk-pass")

    assertEquals(VaultState.Locked, vaults.start().prepare(KeysOpening.IfStored))
  }

  @Test
  fun aKeysetLeftWithNoDataKeyIsNotOpenedByPrepareIfStored() = runTest {
    val first = vaults.start()
    first.sealed(apiKey(connection), "sk-device")
    first.erase(connection)
    val readsBefore = vaults.keychain.reads

    assertEquals(VaultState.Closed, vaults.start().prepare(KeysOpening.IfStored))
    assertEquals(readsBefore, vaults.keychain.reads)
  }

  @Test
  fun aPassphraseKeysetIsLockedByTheLaunchCheck() = runTest {
    vaults.keychain.noKeychain = true
    vaults.start().setPassphrase("correct horse".passphrase())

    assertEquals(VaultState.Locked, vaults.start().prepare(KeysOpening.IfPassphraseProtected))
  }
}

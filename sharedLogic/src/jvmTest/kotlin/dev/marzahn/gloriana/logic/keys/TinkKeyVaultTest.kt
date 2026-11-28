package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.DebugHooks
import dev.marzahn.gloriana.logic.debug.FaultInjected
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TinkKeyVaultTest {
  @TempDir
  lateinit var library: Path

  private val vaults by lazy { Vaults(library) }
  private val connection = Uuid.random()
  private val other = Uuid.random()
  private val dataKeyFile by lazy { library.resolve("keys").resolve("connections.json") }
  private val keysetFile by lazy { library.resolve("keys").resolve("keyset-device.json") }

  @Test
  fun aKeyAndAHeaderValueOpenAfterARestart() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-round-trip")
    val value = vaults.start().sealed(header(connection, "X-Org"), "org-7")

    val restarted = vaults.start()
    assertEquals("sk-round-trip", restarted.opened(apiKey(connection), key))
    assertEquals("org-7", restarted.opened(header(connection, "X-Org"), value))
    assertEquals(VaultState.Unlocked, restarted.state.value)
  }

  @Test
  fun aCiphertextMovedToAnotherConnectionOrHeaderDoesNotOpen() = runTest {
    val vault = vaults.start()
    vault.sealed(apiKey(other), "sk-other")
    val key = vault.sealed(apiKey(connection), "sk-moved")
    val value = vault.sealed(header(connection, "X-Org"), "org-7")

    assertEquals(OpenResult.Missing, vault.open(apiKey(other), key))
    assertEquals(OpenResult.Missing, vault.open(header(connection, "X-Org"), key))
    assertEquals(OpenResult.Missing, vault.open(header(connection, "X-Team"), value))
    assertEquals(OpenResult.Missing, vault.open(apiKey(connection), value))
    assertEquals(OpenResult.Missing, vault.open(header(other, "X-Org"), value))
  }

  @Test
  fun aHeaderNameIsBoundWhateverItsLetterCase() = runTest {
    val vault = vaults.start()
    val value = vault.sealed(header(connection, "X-Org"), "org-7")

    assertEquals("org-7", vault.opened(header(connection, "x-org"), value))
  }

  @Test
  fun eraseRemovesTheDataKeyAtOnce() = runTest {
    val vault = vaults.start()
    val key = vault.sealed(apiKey(connection), "sk-erased")
    val kept = vault.sealed(apiKey(other), "sk-kept")

    vault.erase(connection)

    assertEquals(OpenResult.Missing, vault.open(apiKey(connection), key))
    assertFalse(connection.toString() in Files.readString(dataKeyFile))
    assertEquals(OpenResult.Missing, vaults.start().open(apiKey(connection), key))
    assertEquals("sk-kept", vaults.start().opened(apiKey(other), kept))
  }

  @Test
  fun eraseAndSweepNeverAskTheKeychain() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-erased")
    vaults.keychain.reads = 0
    vaults.keychain.unreachable = true

    val restarted = vaults.start(stored = { emptySet() })
    restarted.erase(connection)

    assertEquals(0, vaults.keychain.reads)
    assertEquals(VaultState.Closed, restarted.state.value)
    assertFalse(connection.toString() in Files.readString(dataKeyFile))
  }

  @Test
  fun aKillBeforeTheDataKeyIsRemovedIsFinishedByTheStartUpSweep() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-killed")
    val kept = vaults.start().sealed(apiKey(other), "sk-kept")
    val dying = vaults.start(faults = { throw FaultInjected(it) })

    assertFailsWith<FaultInjected> { dying.erase(connection) }
    assertTrue(connection.toString() in Files.readString(dataKeyFile))

    val restarted = vaults.start(stored = { setOf(other) })
    restarted.prepare(KeysOpening.IfPassphraseProtected)
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
    assertFalse(connection.toString() in Files.readString(dataKeyFile))
    assertEquals("sk-kept", restarted.opened(apiKey(other), kept))
  }

  @Test
  fun theDebuggableBuildFailsAnEraseAtItsFaultPoint() = runTest {
    Files.createDirectories(library.resolve("debug"))
    Files.writeString(library.resolve("debug").resolve("hooks.properties"), "fault.keys.BeforeDataKeyRemoved=fail")
    val vault = vaults.start(faults = DebugHooks(LibraryDirectory { library }, { true }))
    vault.sealed(apiKey(connection), "sk-held")

    assertFailsWith<FaultInjected> { vault.erase(connection) }
  }

  @Test
  fun theSweepRunsOnceBeforeAnySealSoADataKeyMadeThisRunStaysUntilTheNextStart() = runTest {
    val vault = vaults.start(stored = { emptySet() })
    val key = vault.sealed(apiKey(connection), "sk-saving")

    assertEquals("sk-saving", vault.opened(apiKey(connection), key))
    assertEquals(OpenResult.Missing, vaults.start(stored = { emptySet() }).open(apiKey(connection), key))
  }

  @Test
  fun aSweepThatCannotReadTheStoredConnectionsFailsNothingAndRunsAtTheNextCall() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-orphan")
    var storageOpen = false
    val vault = vaults.start(stored = { if (storageOpen) emptySet() else throw IllegalStateException("closed") })

    vault.prepare(KeysOpening.IfPassphraseProtected)
    assertEquals(VaultState.Unlocked, vault.prepare())
    assertTrue(connection.toString() in Files.readString(dataKeyFile))

    storageOpen = true
    assertEquals(OpenResult.Missing, vault.open(apiKey(connection), key))
    assertFalse(connection.toString() in Files.readString(dataKeyFile))
  }

  @Test
  fun aSweepWhoseWriteFailsFailsNothingAndRunsAtTheNextCall() = runTest {
    val orphan = vaults.start().sealed(apiKey(other), "sk-orphan")
    val vault = vaults.start(stored = { emptySet() })
    val keys = dataKeyFile.parent
    Files.setPosixFilePermissions(keys, PosixFilePermissions.fromString("r-x------"))
    try {
      vault.prepare(KeysOpening.IfPassphraseProtected)
      assertTrue(other.toString() in Files.readString(dataKeyFile))
    } finally {
      Files.setPosixFilePermissions(keys, PosixFilePermissions.fromString("rwx------"))
    }

    assertEquals(OpenResult.Missing, vault.open(apiKey(other), orphan))
    assertFalse(other.toString() in Files.readString(dataKeyFile))
  }

  @Test
  fun aSweepStillDueIsDroppedForTheRunOnceAKeyIsSealed() = runTest {
    vaults.start().sealed(apiKey(other), "sk-orphan")
    var storageOpen = false
    val vault = vaults.start(stored = { if (storageOpen) emptySet() else throw IllegalStateException("closed") })

    val key = vault.sealed(apiKey(connection), "sk-new")
    storageOpen = true

    assertEquals("sk-new", vault.opened(apiKey(connection), key))
    assertTrue(other.toString() in Files.readString(dataKeyFile), "no sweep after a key was sealed this run")
  }

  @Test
  fun theKeychainIsOpenedOnlyWhenAKeyIsNeeded() = runTest {
    val vault = vaults.start()

    assertEquals(VaultState.Closed, vault.state.value)
    assertEquals(0, vaults.keychain.reads)
    assertEquals(VaultState.Unlocked, vault.prepare())
  }

  @Test
  fun aRemovedKeychainEntryIsALostWrappingKey() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-lost")
    vaults.keychain.secrets.clear()

    val restarted = vaults.start()
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
    val entered = restarted.sealed(apiKey(connection), "sk-entered-again")
    assertEquals("sk-entered-again", vaults.start().opened(apiKey(connection), entered))
  }

  @Test
  fun aKeychainThatFailsOnceDestroysNothing() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-kept")
    val keyset = Files.readAllBytes(keysetFile)
    vaults.keychain.unreachable = true

    val failing = vaults.start()
    assertEquals(OpenResult.Blocked(KeysBlocked.Unavailable), failing.open(apiKey(connection), key))
    assertEquals(VaultState.Unavailable, failing.state.value)
    assertEquals(PassphraseResult.NotAvailable, failing.setPassphrase("anything".passphrase()))
    assertTrue(keyset.contentEquals(Files.readAllBytes(keysetFile)))

    vaults.keychain.unreachable = false
    assertEquals("sk-kept", failing.opened(apiKey(connection), key))
    assertEquals("sk-kept", vaults.start().opened(apiKey(connection), key))
  }

  @Test
  fun aKeychainBackendGoneAfterTheKeysetWasMadeLeavesItInPlace() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")
    val keyset = Files.readAllBytes(keysetFile)
    vaults.keychain.noKeychain = true

    assertEquals(VaultState.Unavailable, vaults.start().prepare())
    assertTrue(keyset.contentEquals(Files.readAllBytes(keysetFile)))
  }

  @Test
  fun oneDamagedEntryKeepsTheOtherDataKeys() = runTest {
    val vault = vaults.start()
    val key = vault.sealed(apiKey(connection), "sk-kept")
    Files.writeString(dataKeyFile, Files.readString(dataKeyFile).replace("}", ",\"not-an-id\":\"!!\"}"))

    val restarted = vaults.start()
    restarted.sealed(apiKey(other), "sk-new")
    assertEquals("sk-kept", vaults.start().opened(apiKey(connection), key))
  }

  @Test
  fun aDamagedDataKeyFileReadsAsMissing() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-damaged")
    Files.writeString(dataKeyFile, "{not json")

    assertEquals(OpenResult.Missing, vaults.start().open(apiKey(connection), key))
  }

  @Test
  fun aKeychainSecretThatIsThereButDifferentLeavesTheKeysetAndAsksForAReset() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-kept")
    val keyset = Files.readAllBytes(keysetFile)
    vaults.keychain.replaceEverySecret(ByteArray(32) { 7 })

    val restarted = vaults.start()
    assertEquals(VaultState.DeviceDamaged, restarted.prepare())
    assertEquals(OpenResult.Blocked(KeysBlocked.Unreadable), restarted.open(apiKey(connection), key))
    assertTrue(keyset.contentEquals(Files.readAllBytes(keysetFile)))
    assertEquals(ErasingResult.NothingChanged, restarted.reset("x".passphrase()))

    assertEquals(ErasingResult.Done, restarted.replaceDeviceKeyset())
    assertEquals(VaultState.Unlocked, restarted.state.value)
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
    assertTrue(Files.readString(keysetFile).startsWith("{\"type\":\"device\""))
    assertEquals(1, vaults.keychain.secrets.size)
  }

  @Test
  fun aNotFoundTheBackendCannotConfirmLeavesTheKeysetAndOffersAStartOver() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-kept")
    val keyset = Files.readAllBytes(keysetFile)
    val stored = vaults.keychain.secrets.toMap()
    vaults.keychain.secrets.clear()
    vaults.keychain.notFoundConfirmed = false

    val restarted = vaults.start()
    assertEquals(VaultState.DeviceKeyNotFound, restarted.prepare())
    assertTrue(keyset.contentEquals(Files.readAllBytes(keysetFile)))

    vaults.keychain.secrets.putAll(stored)
    assertEquals("sk-kept", restarted.opened(apiKey(connection), key))
  }

  @Test
  fun theUserCanStartOverWhenTheKeyIsNotFound() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-kept")
    vaults.keychain.secrets.clear()
    vaults.keychain.notFoundConfirmed = false

    val restarted = vaults.start()
    assertEquals(VaultState.DeviceKeyNotFound, restarted.prepare())
    assertEquals(ErasingResult.Done, restarted.replaceDeviceKeyset())
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
  }

  @Test
  fun aReplaceCutShortBeforeTheNewKeysetLandsKeepsTheOldKeyAndFile() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")
    vaults.keychain.replaceEverySecret(ByteArray(32) { 7 })
    val oldKeys = vaults.keychain.secrets.keys.toSet()
    val keyset = Files.readAllBytes(keysetFile)
    val dying = vaults.start(faults = { throw FaultInjected(it) })
    dying.prepare()

    assertFailsWith<FaultInjected> { dying.replaceDeviceKeyset() }

    assertTrue(keyset.contentEquals(Files.readAllBytes(keysetFile)))
    assertTrue(vaults.keychain.secrets.keys.containsAll(oldKeys))
    assertEquals(VaultState.DeviceDamaged, vaults.start().prepare())
  }

  @Test
  fun aStartOverWhoseNewKeysetCantBeWrittenSaysTheKeysWereErasedAndStaysDamaged() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-erased")
    vaults.keychain.replaceEverySecret(ByteArray(32) { 7 })
    val oldKeys = vaults.keychain.secrets.keys.toSet()
    val failing = vaults.start(faults = { if (it == KeyWritePoint.NewKeysetStaged) throw IOException("disk full") })
    failing.prepare()

    assertEquals(ErasingResult.KeysErasedKeysetKept, failing.replaceDeviceKeyset())

    assertEquals(oldKeys, vaults.keychain.secrets.keys.toSet())
    val restarted = vaults.start()
    assertEquals(VaultState.DeviceDamaged, restarted.prepare())
    assertEquals(ErasingResult.Done, restarted.replaceDeviceKeyset())
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
  }

  @Test
  fun aMalformedKeychainEntryIsDamagedAndCanBeStartedOver() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")
    vaults.keychain.malformed += vaults.keychain.secrets.keys

    val restarted = vaults.start()
    assertEquals(VaultState.DeviceDamaged, restarted.prepare())
    assertEquals(ErasingResult.Done, restarted.replaceDeviceKeyset())
  }

  @Test
  fun aFirstStartOnABackendThatCannotConfirmStillMakesAKey() = runTest {
    vaults.keychain.notFoundConfirmed = false

    assertEquals(VaultState.Unlocked, vaults.start().prepare())
  }

  @Test
  fun aKeychainWriteThatDoesNotStickWritesNoKeyset() = runTest {
    vaults.keychain.writesAreLost = true

    assertEquals(VaultState.Unavailable, vaults.start().prepare())
    assertFalse(Files.exists(keysetFile))
  }

  @Test
  fun aKeychainSecretOfTheWrongLengthIsDamagedNotLost() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")
    val keyset = Files.readAllBytes(keysetFile)
    vaults.keychain.replaceEverySecret(ByteArray(5))

    assertEquals(VaultState.DeviceDamaged, vaults.start().prepare())
    assertTrue(keyset.contentEquals(Files.readAllBytes(keysetFile)))
  }

  @Test
  fun aDeviceKeysetWithAnUnreadableHeaderIsDamagedAndStaysOnTheDevice() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")
    Files.writeString(keysetFile, "garbage")

    val restarted = vaults.start()
    assertEquals(VaultState.DeviceDamaged, restarted.prepare())
    assertEquals(ErasingResult.Done, restarted.replaceDeviceKeyset())
    assertEquals(VaultState.Unlocked, vaults.start().prepare())
  }

  @Test
  fun aStartOverWhoseEraseFailsChangesNothing() = runTest {
    vaults.start().sealed(apiKey(connection), "sk-kept")
    vaults.keychain.replaceEverySecret(ByteArray(32) { 7 })
    val restarted = vaults.start()
    restarted.prepare()
    val directory = keysetFile.parent.toFile()
    directory.setWritable(false)
    try {
      assertEquals(ErasingResult.NothingChanged, restarted.replaceDeviceKeyset())
      assertEquals(VaultState.DeviceDamaged, restarted.state.value)
      assertEquals(KeyFileResult.Unavailable, restarted.erase(connection))
    } finally {
      directory.setWritable(true)
    }
    assertEquals(VaultState.DeviceDamaged, restarted.prepare())
  }

  @Test
  fun aPartialWriteLeftByAKillIsRemovedAndTheOldKeysetOpens() = runTest {
    val key = vaults.start().sealed(apiKey(connection), "sk-kept")
    val partial = keysetFile.resolveSibling(".keyset-device.json12345.part")
    Files.writeString(partial, "half a keyset")

    assertEquals("sk-kept", vaults.start().opened(apiKey(connection), key))
    assertFalse(Files.exists(partial))
  }

  @Test
  fun anUnreadableKeysDirectoryIsUnavailableNotACrash() = runTest {
    Files.createDirectories(library)
    Files.writeString(library.resolve("keys"), "a file where the directory belongs")

    val vault = vaults.start()
    assertEquals(VaultState.Unavailable, vault.prepare())
    assertEquals(SealResult.Blocked(KeysBlocked.Unavailable), vault.seal(apiKey(connection), "sk-nowhere"))
  }
}

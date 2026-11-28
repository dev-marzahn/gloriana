package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.debug.FaultInjected
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PassphraseVaultTest {
  @TempDir
  lateinit var library: Path

  private val vaults by lazy { Vaults(library).apply { keychain.noKeychain = true } }
  private val connection = Uuid.random()
  private val keysetFile by lazy { library.resolve("keys").resolve("keyset-passphrase.json") }

  private suspend fun passphraseSet(passphrase: String = "correct horse"): KeyVault =
    vaults.start().also { assertEquals(PassphraseResult.Accepted, it.setPassphrase(passphrase.passphrase())) }

  @Test
  fun withNoKeychainTheVaultAsksForANewPassphraseAndSealsNothingUntilThen() = runTest {
    val vault = vaults.start()

    assertEquals(SealResult.NeedsPassphrase, vault.seal(apiKey(connection), "sk-early"))
    assertEquals(VaultState.NoKeychain, vault.state.value)
    assertEquals(PassphraseResult.NotAvailable, vault.unlock("correct horse".passphrase()))
    assertEquals(PassphraseResult.Accepted, vault.setPassphrase("correct horse".passphrase()))
    assertEquals(VaultState.PassphraseUnlocked, vault.state.value)
    assertEquals(PassphraseResult.NotAvailable, vault.setPassphrase("again".passphrase()))
  }

  @Test
  fun runningOutOfMemoryWhileDerivingReadsAsNotAvailableAndCrashesNothing() = runTest {
    val outOfMemory = KeyDerivation { _, _, _ -> throw OutOfMemoryError("Argon2id") }
    assertEquals(PassphraseResult.NotAvailable, vaults.start(derivation = outOfMemory).setPassphrase("p".passphrase()))
    passphraseSet().sealed(apiKey(connection), "sk-kept")

    val starved = vaults.start(derivation = outOfMemory)
    assertEquals(VaultState.Locked, starved.prepare())
    assertEquals(PassphraseResult.NotAvailable, starved.unlock("correct horse".passphrase()))
    assertEquals(
      PassphraseResult.NotAvailable,
      starved.changePassphrase("correct horse".passphrase(), "new".passphrase()),
    )
    assertEquals(ErasingResult.KeysErasedKeysetKept, vaults.start(derivation = outOfMemory).reset("new".passphrase()))
  }

  @Test
  fun aRestartAsksForThePassphraseAgain() = runTest {
    val key = passphraseSet().sealed(apiKey(connection), "sk-passphrase")

    val restarted = vaults.start()
    assertEquals(OpenResult.Blocked(KeysBlocked.Locked), restarted.open(apiKey(connection), key))
    assertEquals(VaultState.Locked, restarted.state.value)
    assertEquals(PassphraseResult.Accepted, restarted.unlock("correct horse".passphrase()))
    assertEquals("sk-passphrase", restarted.opened(apiKey(connection), key))
  }

  @Test
  fun aWrongPassphraseIsRefusedAndAskedAgain() = runTest {
    passphraseSet()

    val restarted = vaults.start()
    assertEquals(PassphraseResult.WrongPassphrase, restarted.unlock("battery staple".passphrase()))
    assertEquals(VaultState.Locked, restarted.state.value)
    assertEquals(PassphraseResult.Accepted, restarted.unlock("correct horse".passphrase()))
  }

  @Test
  fun aDeviceThatStartedOnThePassphraseStaysOnItWhenAKeychainAppears() = runTest {
    passphraseSet()
    vaults.keychain.noKeychain = false
    vaults.keychain.reads = 0

    assertEquals(VaultState.Locked, vaults.start().prepare())
    assertEquals(0, vaults.keychain.reads)
  }

  @Test
  fun anEditedHeaderFailsLikeAWrongPassphrase() = runTest {
    passphraseSet()
    Files.writeString(keysetFile, Files.readString(keysetFile).replace("\"iterations\":1", "\"iterations\":2"))

    assertEquals(PassphraseResult.WrongPassphrase, vaults.start().unlock("correct horse".passphrase()))
  }

  @Test
  fun aHeaderAskingForTooMuchMemoryIsDamagedNotDerived() = runTest {
    passphraseSet()
    Files.writeString(keysetFile, Files.readString(keysetFile).replace("\"memoryKib\":64", "\"memoryKib\":2147483647"))

    val restarted = vaults.start()
    assertEquals(VaultState.PassphraseDamaged, restarted.prepare())
    assertEquals(PassphraseResult.NotAvailable, restarted.unlock("correct horse".passphrase()))
    val change = restarted.changePassphrase("correct horse".passphrase(), "x".passphrase())
    assertEquals(PassphraseResult.NotAvailable, change)
    assertEquals(ErasingResult.Done, restarted.reset("new passphrase".passphrase()))
    assertEquals(VaultState.PassphraseUnlocked, restarted.state.value)
  }

  @Test
  fun aDamagedPassphraseKeysetKeepsTheKeysLockedUntilAReset() = runTest {
    val key = passphraseSet().sealed(apiKey(connection), "sk-kept")
    Files.writeString(keysetFile, "damaged")

    val restarted = vaults.start()
    assertEquals(VaultState.PassphraseDamaged, restarted.prepare())
    assertEquals(OpenResult.Blocked(KeysBlocked.Locked), restarted.open(apiKey(connection), key))
    assertEquals(SealResult.Blocked(KeysBlocked.Locked), restarted.seal(apiKey(connection), "sk-new"))
  }

  @Test
  fun aDeferredUnlockReadsAnUnreadableKeyFileAsLockedUntilItCanBeRead() = runTest {
    val key = passphraseSet().sealed(apiKey(connection), "sk-kept")
    assertTrue(keysetFile.toFile().setReadable(false))

    val restarted = vaults.start()
    restarted.deferUnlock()
    assertEquals(VaultState.Locked, restarted.state.value)
    assertEquals(OpenResult.Blocked(KeysBlocked.Locked), restarted.open(apiKey(connection), key))
    assertEquals(SealResult.Blocked(KeysBlocked.Locked), restarted.seal(apiKey(connection), "sk-new"))
    assertEquals(VaultState.Locked, restarted.state.value)
    assertEquals(PassphraseResult.NotAvailable, restarted.unlock("correct horse".passphrase()))

    assertTrue(keysetFile.toFile().setReadable(true))
    assertEquals(PassphraseResult.Accepted, restarted.unlock("correct horse".passphrase()))
    assertEquals(VaultState.PassphraseUnlocked, restarted.state.value)
  }

  @Test
  fun aDeferredUnlockEndsOnceTheKeysAreUnlocked() = runTest {
    passphraseSet()
    val restarted = vaults.start()
    restarted.deferUnlock()
    assertEquals(PassphraseResult.Accepted, restarted.unlock("correct horse".passphrase()))
    assertTrue(keysetFile.toFile().setReadable(false))

    val change = restarted.changePassphrase("correct horse".passphrase(), "another one".passphrase())
    assertTrue(keysetFile.toFile().setReadable(true))

    assertEquals(PassphraseResult.NotAvailable, change)
    assertEquals(VaultState.Closed, restarted.state.value)
  }

  @Test
  fun anUnreadableKeyFileWithoutADeferredUnlockStaysUnavailable() = runTest {
    passphraseSet()
    assertTrue(keysetFile.toFile().setReadable(false))

    assertEquals(VaultState.Unavailable, vaults.start().prepare())
    assertTrue(keysetFile.toFile().setReadable(true))
  }

  @Test
  fun aHeaderAskingForMoreThanTheShippedCostIsDamaged() = runTest {
    passphraseSet()
    Files.writeString(keysetFile, Files.readString(keysetFile).replace("\"memoryKib\":64", "\"memoryKib\":65537"))

    assertEquals(VaultState.PassphraseDamaged, vaults.start().prepare())
  }

  @Test
  fun aHeaderWithABadSaltIsDamaged() = runTest {
    passphraseSet()
    Files.writeString(keysetFile, Files.readString(keysetFile).replace(Regex("\"salt\":\"[^\"]*\""), "\"salt\":\"!!\""))

    assertEquals(VaultState.PassphraseDamaged, vaults.start().prepare())
  }

  @Test
  fun aTruncatedPassphraseKeysetStaysOnThePassphrase() = runTest {
    passphraseSet()
    val lines = Files.readAllLines(keysetFile)
    Files.writeString(keysetFile, lines[0] + "\n")
    vaults.keychain.noKeychain = false
    vaults.keychain.reads = 0

    val restarted = vaults.start()
    assertEquals(VaultState.PassphraseDamaged, restarted.prepare())
    assertEquals(0, vaults.keychain.reads)
    assertEquals(ErasingResult.NothingChanged, restarted.replaceDeviceKeyset())
  }

  @Test
  fun resetErasesEveryKeyAndSetsANewPassphrase() = runTest {
    val key = passphraseSet("forgotten").sealed(apiKey(connection), "sk-before-reset")

    val restarted = vaults.start()
    assertEquals(ErasingResult.Done, restarted.reset("new passphrase".passphrase()))

    assertEquals(VaultState.PassphraseUnlocked, restarted.state.value)
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
    assertEquals("{}", Files.readString(library.resolve("keys").resolve("connections.json")))
    val afterReset = vaults.start()
    assertEquals(PassphraseResult.WrongPassphrase, afterReset.unlock("forgotten".passphrase()))
    assertEquals(PassphraseResult.Accepted, afterReset.unlock("new passphrase".passphrase()))
  }

  @Test
  fun aResetKilledBeforeTheNewKeysetLeavesTheKeysErasedAndTheOldPassphrase() = runTest {
    val key = passphraseSet("forgotten").sealed(apiKey(connection), "sk-before-reset")

    assertFailsWith<FaultInjected> {
      vaults.start(faults = { throw FaultInjected(it) }).reset("new passphrase".passphrase())
    }

    val restarted = vaults.start()
    assertEquals(PassphraseResult.Accepted, restarted.unlock("forgotten".passphrase()))
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
  }

  @Test
  fun aResetWhoseNewKeysetCantBeWrittenSaysTheKeysWereErasedAndKeepsTheOldPassphrase() = runTest {
    val key = passphraseSet("forgotten").sealed(apiKey(connection), "sk-before-reset")
    val failing = vaults.start(faults = { if (it == KeyWritePoint.NewKeysetStaged) throw IOException("disk full") })

    assertEquals(ErasingResult.KeysErasedKeysetKept, failing.reset("new passphrase".passphrase()))
    assertEquals(VaultState.Locked, failing.prepare())

    val restarted = vaults.start()
    assertEquals(PassphraseResult.Accepted, restarted.unlock("forgotten".passphrase()))
    assertEquals(OpenResult.Missing, restarted.open(apiKey(connection), key))
  }

  @Test
  fun aLockedVaultAsksForThePassphraseNotANewOne() = runTest {
    passphraseSet()

    assertEquals(SealResult.Blocked(KeysBlocked.Locked), vaults.start().seal(apiKey(connection), "sk-later"))
  }

  @Test
  fun aFallbackDeviceWithAnUnreadableHeaderCanBeReset() = runTest {
    passphraseSet()
    Files.writeString(keysetFile, "garbage")
    vaults.keychain.noKeychain = false

    val restarted = vaults.start()
    assertEquals(VaultState.PassphraseDamaged, restarted.prepare())
    assertEquals(ErasingResult.Done, restarted.reset("new passphrase".passphrase()))
    assertEquals(VaultState.PassphraseUnlocked, restarted.state.value)
  }

  @Test
  fun changingThePassphraseOfADamagedKeysetIsNotAvailableNotWrong() = runTest {
    val vault = passphraseSet()
    Files.writeString(keysetFile, "garbage")

    val result = vault.changePassphrase("correct horse".passphrase(), "new".passphrase())

    assertEquals(PassphraseResult.NotAvailable, result)
  }

  @Test
  fun moreThanOneLaneIsDamaged() = runTest {
    passphraseSet()
    Files.writeString(keysetFile, Files.readString(keysetFile).replace("\"lanes\":1", "\"lanes\":2"))

    assertEquals(VaultState.PassphraseDamaged, vaults.start().prepare())
  }

  @Test
  fun onlyALockedVaultIsReset() = runTest {
    assertEquals(ErasingResult.NothingChanged, passphraseSet().reset("other".passphrase()))
  }

  @Test
  fun changingThePassphraseKeepsTheKeys() = runTest {
    val first = passphraseSet()
    val key = first.sealed(apiKey(connection), "sk-kept")

    val changed = first.changePassphrase("correct horse".passphrase(), "new horse".passphrase())

    assertEquals(PassphraseResult.Accepted, changed)

    val restarted = vaults.start()
    assertEquals(PassphraseResult.WrongPassphrase, restarted.unlock("correct horse".passphrase()))
    assertEquals(PassphraseResult.Accepted, restarted.unlock("new horse".passphrase()))
    assertEquals("sk-kept", restarted.opened(apiKey(connection), key))
  }

  @Test
  fun aChangeWhoseWriteFailsKeepsTheVaultUnlockedAndTheOldPassphrase() = runTest {
    passphraseSet()
    val unlocked = vaults.start(faults = { if (it == KeyWritePoint.NewKeysetStaged) throw IOException("disk full") })
    assertEquals(PassphraseResult.Accepted, unlocked.unlock("correct horse".passphrase()))
    val key = unlocked.sealed(apiKey(connection), "sk-kept")

    val changed = unlocked.changePassphrase("correct horse".passphrase(), "new horse".passphrase())

    assertEquals(PassphraseResult.NotAvailable, changed)
    assertEquals(VaultState.PassphraseUnlocked, unlocked.state.value)
    assertEquals("sk-kept", unlocked.opened(apiKey(connection), key))
    assertEquals(PassphraseResult.Accepted, vaults.start().unlock("correct horse".passphrase()))
  }

  @Test
  fun aLockedVaultChangesItsPassphraseAndUnlocks() = runTest {
    passphraseSet()

    val restarted = vaults.start()
    val changed = restarted.changePassphrase("correct horse".passphrase(), "new".passphrase())

    assertEquals(PassphraseResult.Accepted, changed)
    assertEquals(VaultState.PassphraseUnlocked, restarted.state.value)
  }

  @Test
  fun aWrongCurrentPassphraseChangesNothing() = runTest {
    passphraseSet()
    val before = Files.readAllBytes(keysetFile)

    val result = vaults.start().changePassphrase("battery staple".passphrase(), "new horse".passphrase())

    assertEquals(PassphraseResult.WrongPassphrase, result)
    assertTrue(before.contentEquals(Files.readAllBytes(keysetFile)))
    assertEquals(PassphraseResult.Accepted, vaults.start().unlock("correct horse".passphrase()))
  }

  @Test
  fun aKillBeforeTheNewKeysetReplacesTheOldLeavesTheOldPassphrase() = runTest {
    val key = passphraseSet().sealed(apiKey(connection), "sk-kept")
    val dying = vaults.start(faults = { throw FaultInjected(it) })

    assertFailsWith<FaultInjected> { dying.changePassphrase("correct horse".passphrase(), "new horse".passphrase()) }

    val restarted = vaults.start()
    assertEquals(PassphraseResult.WrongPassphrase, restarted.unlock("new horse".passphrase()))
    assertEquals(PassphraseResult.Accepted, restarted.unlock("correct horse".passphrase()))
    assertEquals("sk-kept", restarted.opened(apiKey(connection), key))
    val left = keysetFile.parent.toFile().list().orEmpty().toSet()
    assertEquals(setOf("keyset-passphrase.json", "connections.json"), left)
  }

  @Test
  fun aKeychainKeysetHasNoPassphraseToChange() = runTest {
    val keychain = Vaults(library.resolve("keychain")).start()

    assertEquals(PassphraseResult.NotAvailable, keychain.changePassphrase("a".passphrase(), "b".passphrase()))
  }

  @Test
  fun theVaultClearsThePassphrasesItIsGiven() = runTest {
    val first = "correct horse".passphrase()
    vaults.start().setPassphrase(first)
    val current = "correct horse".passphrase()
    val new = "new horse".passphrase()
    vaults.start().changePassphrase(current, new)

    listOf(first, current, new).forEach { passphrase -> assertTrue(passphrase.all { it == Char(0) }) }
  }

  @Test
  fun theShippedCostDerivesTheKeyAndIsRecordedInTheHeader() = runTest {
    vaults.start(cost = Argon2idCost.Default).setPassphrase("correct horse".passphrase())

    val header = Files.readAllLines(keysetFile).first()
    assertTrue(header.contains("\"memoryKib\":65536,\"iterations\":3,\"lanes\":1"), header)
    assertEquals(PassphraseResult.Accepted, vaults.start().unlock("correct horse".passphrase()))
  }
}

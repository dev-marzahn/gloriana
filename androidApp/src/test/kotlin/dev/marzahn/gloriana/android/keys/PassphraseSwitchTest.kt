package dev.marzahn.gloriana.android.keys

import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.DebugHooks
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.keys.DeviceKey
import dev.marzahn.gloriana.logic.keys.NewDeviceKey
import dev.marzahn.gloriana.logic.keys.VaultState
import dev.marzahn.gloriana.logic.keys.keyVault
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PassphraseSwitchTest {
  @TempDir
  lateinit var library: Path

  private val keystore = KeystoreKeyStore(SoftwareKeys())

  @Test
  fun theSwitchForcesThePassphraseInADebuggableBuild() {
    switchOn()

    assertEquals(NewDeviceKey.NoStore, switch(debuggable = true).create())
    assertEquals(DeviceKey.NoStore, switch(debuggable = true).find("any"))
  }

  @Test
  fun aReleaseBuildIgnoresTheSwitch() {
    switchOn()

    assertIs<NewDeviceKey.Made>(switch(debuggable = false).create())
  }

  @Test
  fun withTheSwitchOffTheKeystoreIsUsed() {
    assertIs<NewDeviceKey.Made>(switch(debuggable = true).create())
  }

  @Test
  fun aFreshLibraryWithTheSwitchOnAsksForAPassphrase() = runBlocking {
    switchOn()

    assertEquals(VaultState.NoKeychain, vault().prepare())
  }

  @Test
  fun theSwitchTurnedOnOverAKeystoreKeysetLeavesItUnavailableSoQaClearsAppDataFirst() = runBlocking {
    assertEquals(VaultState.Unlocked, vault().prepare())
    switchOn()

    assertEquals(VaultState.Unavailable, vault().prepare())
  }

  private fun vault() = keyVault(LibraryDirectory { library }, switch(debuggable = true), Faults.None)

  private fun switch(debuggable: Boolean) =
    PassphraseSwitch(keystore, DebugHooks(LibraryDirectory { library }, { debuggable }))

  private fun switchOn() {
    Files.createDirectories(library.resolve("debug"))
    Files.writeString(library.resolve("debug").resolve("hooks.properties"), "${KeySwitch.ForcePassphrase.key}=on")
  }
}

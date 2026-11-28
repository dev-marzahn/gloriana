package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.KeyFindings
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysOpening
import dev.marzahn.gloriana.logic.keys.VaultState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private class PreparedAs(vault: KeyVault, private val prepared: VaultState) : KeyVault by vault {
  override suspend fun prepare(opening: KeysOpening): VaultState = prepared
}

private class FindingsFail(vault: KeyVault, private val prepared: VaultState) : KeyVault by vault {
  override val findings: Flow<KeyFindings> = flow { throw IOException("the key file is unreadable") }

  override suspend fun prepare(opening: KeysOpening): VaultState = prepared
}

class KeysUnlockTest {
  @TempDir
  lateinit var directory: Path

  private val library by lazy { ConnectionLibrary(directory) }

  @AfterEach
  fun closeLibrary() = library.close()

  private suspend fun holdingOne() {
    library.created(draft(name = "Work", key = KeyInput.Entered("sk-1")))
    library.created(draft(name = "No keys"))
  }

  @Test
  fun theLaunchAsksForWhatTheVaultNeedsWithTheHoldersCounted() = runTest {
    holdingOne()

    val asks = VaultState.entries.associateWith { state ->
      KeysUnlock(PreparedAs(library.vault, state), library.repository).askAtLaunch()
    }

    assertEquals(PassphraseAsk.Unlock(holding = 1), asks.getValue(VaultState.Locked))
    assertEquals(PassphraseAsk.Damaged(holding = 1), asks.getValue(VaultState.PassphraseDamaged))
    assertEquals(PassphraseAsk.CouldNotCheck, asks.getValue(VaultState.Unavailable))
    val asking = setOf(VaultState.Locked, VaultState.PassphraseDamaged, VaultState.Unavailable)
    assertEquals(setOf(PassphraseAsk.None), (asks - asking).values.toSet())
  }

  @Test
  fun aCountThatFailsCannotBeCheckedAtLaunch() = runTest {
    holdingOne()
    val failing = { state: VaultState -> KeysUnlock(FindingsFail(library.vault, state), library.repository) }

    assertEquals(null, failing(VaultState.Locked).holding())
    assertEquals(PassphraseAsk.CouldNotCheck, failing(VaultState.Locked).askAtLaunch())
    assertEquals(PassphraseAsk.CouldNotCheck, failing(VaultState.PassphraseDamaged).askAtLaunch())
  }
}

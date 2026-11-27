package dev.marzahn.gloriana.ui.settings

import dev.marzahn.gloriana.logic.keys.VaultState
import dev.marzahn.gloriana.ui.connections.WriteFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class KeysNoteTest {
  @Test
  fun anUnknownCountHidesTheNoteThatWouldClaimOne() {
    assertEquals(KeysNote.None, keysNote(holding = null, VaultState.DeviceDamaged, StartOverStep.Idle))
    assertEquals(KeysNote.Unreadable(0), keysNote(holding = 0, VaultState.DeviceDamaged, StartOverStep.Idle))
  }

  @Test
  fun aFailedStartOverStaysWhetherOrNotTheCountIsKnown() {
    val failed = StartOverStep.Failed(WriteFailure.KeysErased)

    assertEquals(KeysNote.StartOverFailed(WriteFailure.KeysErased, null), keysNote(null, VaultState.Closed, failed))
    assertEquals(KeysNote.StartOverFailed(WriteFailure.KeysErased, 2), keysNote(2, VaultState.DeviceDamaged, failed))
  }

  @Test
  fun anOpenSheetKeepsTheNoteWhileTheVaultNeedsIt() {
    val confirming = StartOverStep.Confirming(holding = 1)

    assertEquals(KeysNote.Unreadable(1), keysNote(1, VaultState.DeviceKeyNotFound, confirming))
    assertEquals(KeysNote.None, keysNote(1, VaultState.Unlocked, confirming))
  }
}

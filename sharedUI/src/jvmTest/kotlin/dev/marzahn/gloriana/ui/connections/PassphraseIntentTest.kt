package dev.marzahn.gloriana.ui.connections

import kotlin.test.Test
import kotlin.test.assertFalse

private const val PASSPHRASE = "correct horse battery"

class PassphraseIntentTest {
  @Test
  fun noPassphraseIntentOrEntryPrintsWhatWasTyped() {
    val typed = SecretText(PASSPHRASE)
    val printed = listOf(
      PageIntent.TypePassphrase(typed),
      PageIntent.TypePassphraseAgain(typed),
      ChangeIntent.TypeCurrent(typed),
      ChangeIntent.TypeNew(typed),
      ChangeIntent.TypeAgain(typed),
      GateIntent.TypePassphrase(typed),
      GateIntent.TypeNewPassphrase(typed),
      GateIntent.TypeNewPassphraseAgain(typed),
      NewPassphraseEntry().typedPassphrase(typed).typedAgain(typed),
      UnlockEntry().typed(typed),
    ).map { it.toString() }

    printed.forEach { assertFalse(PASSPHRASE in it, it) }
  }
}

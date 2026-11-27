package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.keys.ErasingResult
import dev.marzahn.gloriana.logic.keys.NewPassphraseProblem
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.keys.newPassphraseProblem

enum class WriteFailure { NothingChanged, KeysErased }

sealed interface EntryStep {
  data object Typing : EntryStep

  data object Busy : EntryStep

  data class Failed(val failure: WriteFailure) : EntryStep
}

data class NewPassphraseEntry(
  val passphrase: SecretText = SecretText.Empty,
  val again: SecretText = SecretText.Empty,
  val problem: NewPassphraseProblem? = null,
  val step: EntryStep = EntryStep.Typing,
) {
  val busy: Boolean get() = step == EntryStep.Busy

  fun typedPassphrase(text: SecretText) = copy(passphrase = text, problem = null)

  fun typedAgain(text: SecretText) = copy(again = text, problem = null)
}

sealed interface Submitted<out R> {
  data object Refused : Submitted<Nothing>

  data class Written<R>(val result: R) : Submitted<R>
}

suspend fun <R> NewPassphraseEntry.submit(
  show: (NewPassphraseEntry) -> Unit,
  write: suspend (CharArray) -> R,
  failureOf: (R) -> WriteFailure?,
): Submitted<R> {
  val problem = newPassphraseProblem(passphrase.text, again.text)
  if (problem != null) {
    show(copy(problem = problem))
    return Submitted.Refused
  }
  show(copy(step = EntryStep.Busy))
  val result = write(passphrase.text.toCharArray())
  show(copy(step = failureOf(result)?.let(EntryStep::Failed) ?: EntryStep.Typing))
  return Submitted.Written(result)
}

fun PassphraseResult.failure(): WriteFailure? = when (this) {
  PassphraseResult.Accepted -> null
  PassphraseResult.WrongPassphrase, PassphraseResult.NotAvailable -> WriteFailure.NothingChanged
}

fun ErasingResult.failure(): WriteFailure? = when (this) {
  ErasingResult.Done -> null
  ErasingResult.NothingChanged -> WriteFailure.NothingChanged
  ErasingResult.KeysErasedKeysetKept -> WriteFailure.KeysErased
}

enum class UnlockStep { Typing, Checking, Wrong, CouldNotCheck }

data class UnlockEntry(val passphrase: SecretText = SecretText.Empty, val step: UnlockStep = UnlockStep.Typing) {
  val checking: Boolean get() = step == UnlockStep.Checking

  fun typed(text: SecretText) = UnlockEntry(text, UnlockStep.Typing)

  fun couldNotCheck() = copy(step = UnlockStep.CouldNotCheck)

  fun retried() = copy(step = UnlockStep.Typing)
}

suspend fun UnlockEntry.submit(
  show: (UnlockEntry) -> Unit,
  unlock: suspend (CharArray) -> PassphraseResult,
): PassphraseResult {
  show(copy(step = UnlockStep.Checking))
  val result = unlock(passphrase.text.toCharArray())
  val step = when (result) {
    PassphraseResult.Accepted -> UnlockStep.Typing
    PassphraseResult.WrongPassphrase -> UnlockStep.Wrong
    PassphraseResult.NotAvailable -> UnlockStep.CouldNotCheck
  }
  show(copy(step = step))
  return result
}

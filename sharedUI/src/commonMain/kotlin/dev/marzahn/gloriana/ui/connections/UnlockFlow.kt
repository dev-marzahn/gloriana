package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.countHoldingOpenableSecrets
import dev.marzahn.gloriana.logic.keys.ErasingResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.keys.VaultState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface UnlockPrompt {
  val busy: Boolean

  data class Unlocking(val entry: UnlockEntry = UnlockEntry()) : UnlockPrompt {
    override val busy: Boolean get() = entry.checking
  }

  sealed interface Damaged : UnlockPrompt {
    override val busy: Boolean get() = false
  }

  data object CountingDamaged : Damaged

  data class CountedDamaged(val holding: Int) : Damaged

  data object DamagedNotCounted : Damaged

  data class Resetting(val entry: NewPassphraseEntry, val holding: Int) : UnlockPrompt {
    override val busy: Boolean get() = entry.busy
  }
}

sealed interface PassphraseIntent {
  data class Type(val text: SecretText) : NewPassphraseIntent

  data class TypeAgain(val text: SecretText) : NewPassphraseIntent

  data object Submit : NewPassphraseIntent

  data object Cancel : NewPassphraseIntent

  data object Forgot : PassphraseIntent

  data object Retry : PassphraseIntent
}

sealed interface NewPassphraseIntent : PassphraseIntent

internal interface UnlockPromptSlot {
  val prompt: UnlockPrompt?

  fun show(prompt: UnlockPrompt?)
}

internal class UnlockFlow(
  private val vault: KeyVault,
  private val repository: ConnectionRepository,
  private val scope: CoroutineScope,
  private val slot: UnlockPromptSlot,
) {
  fun open(place: (UnlockPrompt) -> Unit) {
    if (vault.state.value != VaultState.PassphraseDamaged) {
      place(UnlockPrompt.Unlocking())
      return
    }
    place(UnlockPrompt.CountingDamaged)
    countDamaged()
  }

  private fun countDamaged() {
    scope.launch {
      val holding = holding()
      if (slot.prompt != UnlockPrompt.CountingDamaged) return@launch
      slot.show(holding?.let(UnlockPrompt::CountedDamaged) ?: UnlockPrompt.DamagedNotCounted)
    }
  }

  private fun retry(prompt: UnlockPrompt) {
    when (prompt) {
      is UnlockPrompt.Unlocking -> slot.show(prompt.copy(prompt.entry.retried()))
      UnlockPrompt.DamagedNotCounted -> {
        slot.show(UnlockPrompt.CountingDamaged)
        countDamaged()
      }
      UnlockPrompt.CountingDamaged, is UnlockPrompt.CountedDamaged, is UnlockPrompt.Resetting -> Unit
    }
  }

  fun on(intent: PassphraseIntent, onUnlocked: () -> Unit) {
    val prompt = slot.prompt ?: return
    when (intent) {
      is PassphraseIntent.Type -> slot.show(prompt.typed(intent.text))
      is PassphraseIntent.TypeAgain -> slot.show(prompt.typedAgain(intent.text))
      PassphraseIntent.Submit -> submit(prompt, onUnlocked)
      PassphraseIntent.Forgot -> forgot(prompt)
      PassphraseIntent.Retry -> retry(prompt)
      PassphraseIntent.Cancel -> if (!prompt.busy) slot.show(null)
    }
  }

  private suspend fun holding(): Int? = vault.countHoldingOpenableSecrets(repository.connections())

  private fun submit(prompt: UnlockPrompt, onUnlocked: () -> Unit) {
    if (prompt.busy) return
    scope.launch {
      val unlocked = when (prompt) {
        is UnlockPrompt.Unlocking ->
          prompt.entry.submit({ slot.show(UnlockPrompt.Unlocking(it)) }) { vault.unlock(it) } ==
            PassphraseResult.Accepted
        is UnlockPrompt.Resetting -> prompt.entry.submit(
          show = { slot.show(prompt.copy(entry = it)) },
          write = { vault.reset(it) },
          failureOf = ErasingResult::failure,
        ) == Submitted.Written(ErasingResult.Done)
        is UnlockPrompt.Damaged -> false
      }
      if (unlocked) {
        slot.show(null)
        onUnlocked()
      }
    }
  }

  private fun forgot(prompt: UnlockPrompt) {
    when (prompt) {
      is UnlockPrompt.CountedDamaged -> slot.show(UnlockPrompt.Resetting(NewPassphraseEntry(), prompt.holding))
      is UnlockPrompt.Unlocking -> if (!prompt.busy) {
        scope.launch {
          val holding = holding()
          if (slot.prompt != prompt) return@launch
          slot.show(
            when (holding) {
              null -> UnlockPrompt.Unlocking(prompt.entry.couldNotCheck())
              else -> UnlockPrompt.Resetting(NewPassphraseEntry(), holding)
            },
          )
        }
      }
      UnlockPrompt.CountingDamaged, UnlockPrompt.DamagedNotCounted, is UnlockPrompt.Resetting -> Unit
    }
  }
}

private fun UnlockPrompt.typed(text: SecretText): UnlockPrompt = when (this) {
  is UnlockPrompt.Unlocking -> copy(entry = entry.typed(text))
  is UnlockPrompt.Resetting -> copy(entry = entry.typedPassphrase(text))
  is UnlockPrompt.Damaged -> this
}

private fun UnlockPrompt.typedAgain(text: SecretText): UnlockPrompt = when (this) {
  is UnlockPrompt.Resetting -> copy(entry = entry.typedAgain(text))
  is UnlockPrompt.Unlocking, is UnlockPrompt.Damaged -> this
}

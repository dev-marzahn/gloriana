package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.keys.ErasingResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PagePassphrase(
  private val context: PageContext,
  private val vault: KeyVault,
  private val repository: ConnectionRepository,
) {
  private val session = context.session

  private val activity: PageActivity get() = session.value.activity

  fun on(intent: PageIntent.PassphraseIntent, continueWith: (AfterUnlock) -> Unit) {
    when (intent) {
      is PageIntent.TypePassphrase -> retype { it.typedPassphrase(intent.text) }
      is PageIntent.TypePassphraseAgain -> retype { it.typedAgain(intent.text) }
      PageIntent.SubmitPassphrase -> submit(continueWith)
      PageIntent.ForgotPassphrase -> forgot()
      PageIntent.RetryPassphrase ->
        activity.prompt.unlocking()?.let { context.activity(it.copy(entry = it.entry.retried())) }
      PageIntent.CancelPassphrase -> if (!activity.isBusyPrompt) context.activity(PageActivity.Idle)
    }
  }

  private fun forgot() {
    val unlocking = activity.prompt.unlocking()?.takeUnless { it.entry.checking } ?: return
    context.scope.launch {
      val holding = repository.connections().map { list -> list.count { it.holdsSecrets } }.catch { }.firstOrNull()
      context.activity(
        when (holding) {
          null -> unlocking.copy(entry = unlocking.entry.couldNotCheck())
          else -> PageActivity.Resetting(NewPassphraseEntry(), holding, unlocking.then)
        },
      )
    }
  }

  private fun retype(change: (PageActivity.Prompt) -> PageActivity) {
    session.update { current ->
      current.activity.prompt?.let { current.copy(activity = change(it)) } ?: current
    }
  }

  private fun submit(continueWith: (AfterUnlock) -> Unit) {
    when (val prompt = activity.prompt) {
      is PageActivity.SettingPassphrase -> setPassphrase(prompt.entry, continueWith)
      is PageActivity.Unlocking -> unlock(prompt, continueWith)
      is PageActivity.Resetting -> reset(prompt, continueWith)
      null -> Unit
    }
  }

  private fun setPassphrase(entry: NewPassphraseEntry, continueWith: (AfterUnlock) -> Unit) {
    if (entry.busy) return
    context.scope.launch {
      val submitted = entry.submit(
        show = { context.activity(PageActivity.SettingPassphrase(it)) },
        write = { vault.setPassphrase(it) },
        failureOf = PassphraseResult::failure,
      )
      if (submitted == Submitted.Written(PassphraseResult.Accepted)) done(AfterUnlock.Save, continueWith)
    }
  }

  private fun unlock(prompt: PageActivity.Unlocking, continueWith: (AfterUnlock) -> Unit) {
    if (prompt.entry.checking) return
    context.scope.launch {
      val result = prompt.entry.submit({ context.activity(prompt.copy(entry = it)) }) { vault.unlock(it) }
      if (result == PassphraseResult.Accepted) done(prompt.then, continueWith)
    }
  }

  private fun reset(prompt: PageActivity.Resetting, continueWith: (AfterUnlock) -> Unit) {
    if (prompt.entry.busy) return
    context.scope.launch {
      val submitted = prompt.entry.submit(
        show = { context.activity(prompt.copy(entry = it)) },
        write = { vault.reset(it) },
        failureOf = ErasingResult::failure,
      )
      if (submitted == Submitted.Written(ErasingResult.Done)) done(prompt.then, continueWith)
    }
  }

  private fun done(then: AfterUnlock, continueWith: (AfterUnlock) -> Unit) {
    context.activity(PageActivity.Idle)
    continueWith(then)
  }
}

private fun PageActivity.Prompt.typedPassphrase(text: String): PageActivity.Prompt = when (this) {
  is PageActivity.SettingPassphrase -> copy(entry = entry.typedPassphrase(text))
  is PageActivity.Unlocking -> copy(entry = entry.typed(text))
  is PageActivity.Resetting -> copy(entry = entry.typedPassphrase(text))
}

private fun PageActivity.Prompt.typedAgain(text: String): PageActivity.Prompt = when (this) {
  is PageActivity.SettingPassphrase -> copy(entry = entry.typedAgain(text))
  is PageActivity.Unlocking -> this
  is PageActivity.Resetting -> copy(entry = entry.typedAgain(text))
}

private val PageActivity.prompt: PageActivity.Prompt?
  get() = when (this) {
    is PageActivity.Prompt -> this
    PageActivity.Idle, PageActivity.Saving, PageActivity.Testing, PageActivity.MakingDefault,
    PageActivity.ConfirmingDelete, PageActivity.Deleting, is PageActivity.PickingModel,
    -> null
  }

private fun PageActivity.Prompt?.unlocking(): PageActivity.Unlocking? = when (this) {
  is PageActivity.Unlocking -> this
  is PageActivity.SettingPassphrase, is PageActivity.Resetting, null -> null
}

private val PageActivity.isBusyPrompt: Boolean
  get() = when (val prompt = prompt) {
    is PageActivity.SettingPassphrase -> prompt.entry.busy
    is PageActivity.Unlocking -> prompt.entry.checking
    is PageActivity.Resetting -> prompt.entry.busy
    null -> false
  }

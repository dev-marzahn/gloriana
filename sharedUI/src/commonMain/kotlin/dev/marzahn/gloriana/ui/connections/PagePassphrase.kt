package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PagePassphrase(
  private val context: PageContext,
  private val vault: KeyVault,
  private val unlocking: UnlockFlow,
) {
  private val session = context.session

  fun ask(then: AfterUnlock) = unlocking.open { context.activity(PageActivity.Unlocking(it, then)) }

  fun unlock(intent: PassphraseIntent, continueWith: (AfterUnlock) -> Unit) {
    val asking = session.value.activity as? PageActivity.Unlocking ?: return
    unlocking.on(intent) { continueWith(asking.then) }
  }

  fun set(intent: NewPassphraseIntent, continueWith: (AfterUnlock) -> Unit) {
    val setting = session.value.activity as? PageActivity.SettingPassphrase ?: return
    when (intent) {
      is PassphraseIntent.Type -> retype { it.typedPassphrase(intent.text) }
      is PassphraseIntent.TypeAgain -> retype { it.typedAgain(intent.text) }
      PassphraseIntent.Submit -> setPassphrase(setting.entry, continueWith)
      PassphraseIntent.Cancel -> if (!setting.entry.busy) context.activity(PageActivity.Idle)
    }
  }

  private fun retype(change: (NewPassphraseEntry) -> NewPassphraseEntry) {
    session.update { current ->
      val setting = current.activity as? PageActivity.SettingPassphrase ?: return@update current
      current.copy(activity = PageActivity.SettingPassphrase(change(setting.entry)))
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
      if (submitted == Submitted.Written(PassphraseResult.Accepted)) {
        context.activity(PageActivity.Idle)
        continueWith(AfterUnlock.Save)
      }
    }
  }
}

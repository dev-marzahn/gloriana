package dev.marzahn.gloriana.ui.connections

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.resources.set_passphrase_and_save
import dev.marzahn.gloriana.ui.resources.set_passphrase_failed
import dev.marzahn.gloriana.ui.resources.set_passphrase_failed_detail
import dev.marzahn.gloriana.ui.resources.set_passphrase_text
import dev.marzahn.gloriana.ui.resources.set_passphrase_title
import dev.marzahn.gloriana.ui.resources.setting
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PageSheets(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val onPassphrase = { intent: PassphraseIntent -> onIntent(PageIntent.Passphrase(intent)) }
  when (val activity = page.activity) {
    PageActivity.ConfirmingDelete, PageActivity.Deleting -> page.saved?.let { saved ->
      DeleteConnectionConfirmation(
        saved,
        page.isDefault,
        activity == PageActivity.Deleting,
        onConfirm = { onIntent(PageIntent.ConfirmDelete) },
        onDismiss = { onIntent(PageIntent.CancelDelete) },
      )
    }
    is PageActivity.PickingModel -> ModelPickerSheet(
      host = hostOf(page.form.baseUrl),
      list = activity.list,
      current = page.form.model,
      onPick = { onIntent(PageIntent.PickModel(it)) },
      onRetry = { onIntent(PageIntent.OpenModelList) },
      onDismiss = { onIntent(PageIntent.CloseModelList) },
    )
    is PageActivity.SettingPassphrase -> {
      val onSetting = { intent: NewPassphraseIntent -> onIntent(PageIntent.SetPassphrase(intent)) }
      PassphraseSheet(
        setPassphraseText(),
        activity.entry,
        newPassphraseActions(onSetting),
        ConfirmStyle.Primary,
        onCancel = { onSetting(PassphraseIntent.Cancel) },
      )
    }
    is PageActivity.Unlocking -> UnlockPromptSheet(activity.prompt, onPassphrase)
    PageActivity.Idle, PageActivity.Saving, PageActivity.OpeningSaved, PageActivity.Testing,
    PageActivity.MakingDefault,
    -> Unit
  }
}

@Composable
private fun setPassphraseText(): PassphraseSheetText {
  val failed = FailureText(
    stringResource(Res.string.set_passphrase_failed),
    stringResource(Res.string.set_passphrase_failed_detail),
  )
  return PassphraseSheetText(
    title = stringResource(Res.string.set_passphrase_title),
    text = stringResource(Res.string.set_passphrase_text),
    fieldLabel = stringResource(Res.string.passphrase),
    confirm = stringResource(Res.string.set_passphrase_and_save),
    busyConfirm = stringResource(Res.string.setting),
    failure = { failed },
  )
}

package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.logic.connection.api.hostOf
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.keys_not_checked
import dev.marzahn.gloriana.ui.resources.keys_not_checked_detail
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.resources.set_passphrase_and_save
import dev.marzahn.gloriana.ui.resources.set_passphrase_failed
import dev.marzahn.gloriana.ui.resources.set_passphrase_failed_detail
import dev.marzahn.gloriana.ui.resources.set_passphrase_text
import dev.marzahn.gloriana.ui.resources.set_passphrase_title
import dev.marzahn.gloriana.ui.resources.setting
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PageSheets(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
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
    is PageActivity.Prompt -> PromptSheet(activity, onIntent)
    PageActivity.Idle, PageActivity.Saving, PageActivity.Testing, PageActivity.MakingDefault -> Unit
  }
}

@Composable
private fun PromptSheet(prompt: PageActivity.Prompt, onIntent: (PageIntent) -> Unit) {
  val newPassphrase = NewPassphraseActions(
    onPassphrase = { onIntent(PageIntent.TypePassphrase(it)) },
    onAgain = { onIntent(PageIntent.TypePassphraseAgain(it)) },
    onSubmit = { onIntent(PageIntent.SubmitPassphrase) },
  )
  val cancel = { onIntent(PageIntent.CancelPassphrase) }
  when (prompt) {
    is PageActivity.SettingPassphrase ->
      PassphraseSheet(setPassphraseText(), prompt.entry, newPassphrase, ConfirmStyle.Primary, onCancel = cancel)
    is PageActivity.Resetting -> PassphraseSheet(
      resetSheetText(prompt.holding),
      prompt.entry,
      newPassphrase,
      ConfirmStyle.Danger,
      onCancel = cancel,
    )
    is PageActivity.Unlocking -> UnlockSheet(prompt.entry, onIntent)
  }
}

@Composable
private fun UnlockSheet(entry: UnlockEntry, onIntent: (PageIntent) -> Unit) {
  val dismiss = { if (!entry.checking) onIntent(PageIntent.CancelPassphrase) }
  val actions = UnlockActions(
    onType = { onIntent(PageIntent.TypePassphrase(it)) },
    onUnlock = { onIntent(PageIntent.SubmitPassphrase) },
    onOpenReset = { onIntent(PageIntent.ForgotPassphrase) },
  )
  val spacing = GlorianaTheme.spacing
  GlorianaSheet(dismiss, dismissible = !entry.checking) {
    UnlockHeading(Modifier.padding(bottom = spacing.s16))
    val couldNotCheck = entry.step == UnlockStep.CouldNotCheck
    if (couldNotCheck) {
      RetryNote(stringResource(Res.string.keys_not_checked), Res.string.keys_not_checked_detail) {
        onIntent(PageIntent.RetryPassphrase)
      }
    } else {
      UnlockField(entry, actions)
      ForgotButton(entry, actions.onOpenReset, Modifier.padding(top = spacing.s8))
    }
    SheetButtons(Modifier.padding(top = spacing.s24)) { buttonModifier ->
      if (!couldNotCheck) UnlockButton(entry, actions.onUnlock, buttonModifier)
      GlorianaButton(
        stringResource(Res.string.cancel),
        dismiss,
        buttonModifier,
        kind = ButtonKind.Ghost,
        enabled = !entry.checking,
      )
    }
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

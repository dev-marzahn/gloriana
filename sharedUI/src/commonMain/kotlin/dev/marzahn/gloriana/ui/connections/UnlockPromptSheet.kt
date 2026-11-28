package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.damaged_title
import dev.marzahn.gloriana.ui.resources.ic_restart_alt_20
import dev.marzahn.gloriana.ui.resources.keys_not_checked
import dev.marzahn.gloriana.ui.resources.keys_not_checked_detail
import dev.marzahn.gloriana.ui.resources.reset
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

internal fun newPassphraseActions(onIntent: (NewPassphraseIntent) -> Unit) = NewPassphraseActions(
  onPassphrase = { onIntent(PassphraseIntent.Type(it)) },
  onAgain = { onIntent(PassphraseIntent.TypeAgain(it)) },
  onSubmit = { onIntent(PassphraseIntent.Submit) },
)

@Composable
fun UnlockPromptSheet(prompt: UnlockPrompt, onIntent: (PassphraseIntent) -> Unit) {
  when (prompt) {
    is UnlockPrompt.Unlocking -> UnlockSheet(prompt.entry, onIntent)
    is UnlockPrompt.Damaged -> DamagedSheet(prompt, onIntent)
    is UnlockPrompt.Resetting -> PassphraseSheet(
      resetSheetText(prompt.holding),
      prompt.entry,
      newPassphraseActions(onIntent),
      ConfirmStyle.Danger,
      onCancel = { onIntent(PassphraseIntent.Cancel) },
    )
  }
}

@Composable
private fun DamagedSheet(prompt: UnlockPrompt.Damaged, onIntent: (PassphraseIntent) -> Unit) {
  val dismiss = { onIntent(PassphraseIntent.Cancel) }
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val holding = (prompt as? UnlockPrompt.CountedDamaged)?.holding
  GlorianaSheet(dismiss) {
    Text(stringResource(Res.string.damaged_title), style = type.headlineSmall, color = colors.text)
    if (holding != null) {
      Text(
        damagedText(holding),
        Modifier.padding(top = spacing.s8),
        style = type.bodyMedium,
        color = colors.textMuted,
      )
    }
    val notCounted = prompt == UnlockPrompt.DamagedNotCounted
    if (notCounted) {
      RetryNote(
        stringResource(Res.string.keys_not_checked),
        Res.string.keys_not_checked_detail,
        Modifier.padding(top = spacing.s16),
      ) { onIntent(PassphraseIntent.Retry) }
    }
    SheetButtons(Modifier.padding(top = spacing.s24)) { buttonModifier ->
      if (!notCounted) {
        GlorianaButton(
          stringResource(Res.string.reset),
          { onIntent(PassphraseIntent.Forgot) },
          buttonModifier,
          kind = ButtonKind.Primary,
          icon = Res.drawable.ic_restart_alt_20,
          enabled = holding != null,
        )
      }
      GlorianaButton(stringResource(Res.string.cancel), dismiss, buttonModifier, kind = ButtonKind.Ghost)
    }
  }
}

@Composable
private fun UnlockSheet(entry: UnlockEntry, onIntent: (PassphraseIntent) -> Unit) {
  val dismiss = { if (!entry.checking) onIntent(PassphraseIntent.Cancel) }
  val actions = UnlockActions(
    onType = { onIntent(PassphraseIntent.Type(it)) },
    onUnlock = { onIntent(PassphraseIntent.Submit) },
    onOpenReset = { onIntent(PassphraseIntent.Forgot) },
  )
  val spacing = GlorianaTheme.spacing
  GlorianaSheet(dismiss, dismissible = !entry.checking) {
    UnlockHeading(Modifier.padding(bottom = spacing.s16))
    val couldNotCheck = entry.step == UnlockStep.CouldNotCheck
    if (couldNotCheck) {
      RetryNote(stringResource(Res.string.keys_not_checked), Res.string.keys_not_checked_detail) {
        onIntent(PassphraseIntent.Retry)
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

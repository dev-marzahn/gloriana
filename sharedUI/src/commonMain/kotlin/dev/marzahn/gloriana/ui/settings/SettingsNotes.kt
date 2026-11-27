package dev.marzahn.gloriana.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.delete_failed
import dev.marzahn.gloriana.ui.resources.delete_failed_detail
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.ic_restart_alt_20
import dev.marzahn.gloriana.ui.resources.keys_not_erased
import dev.marzahn.gloriana.ui.resources.keys_unreadable
import dev.marzahn.gloriana.ui.resources.keys_unreadable_detail
import dev.marzahn.gloriana.ui.resources.not_made_default
import dev.marzahn.gloriana.ui.resources.not_made_default_detail
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.start_over
import dev.marzahn.gloriana.ui.resources.start_over_failed_detail
import dev.marzahn.gloriana.ui.resources.start_over_keys_erased
import dev.marzahn.gloriana.ui.resources.start_over_keys_erased_detail
import dev.marzahn.gloriana.ui.connections.WriteFailure
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ConnectionNotes(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    (state.keys as? KeysNote.Unreadable)?.let { UnreadableKeysNote(it, onIntent) }
    when (val activity = state.rowActivity) {
      is RowActivity.DeleteFailed -> RetryNote(
        stringResource(Res.string.delete_failed, activity.row.connection.fields.name),
        Res.string.delete_failed_detail,
      ) { onIntent(SettingsIntent.AskToDelete(activity.row)) }
      is RowActivity.NotMadeDefault -> RetryNote(
        stringResource(Res.string.not_made_default, activity.row.connection.fields.name),
        Res.string.not_made_default_detail,
      ) { onIntent(SettingsIntent.MakeDefault(activity.row)) }
      RowActivity.Idle, is RowActivity.MakingDefault, is RowActivity.ConfirmingDelete, is RowActivity.Deleting -> Unit
    }
  }
}

@Composable
private fun UnreadableKeysNote(keys: KeysNote.Unreadable, onIntent: (SettingsIntent) -> Unit) {
  val startOverFocus = remember { FocusRequester() }
  LaunchedEffect(keys.step) { if (keys.step is StartOverStep.Failed) startOverFocus.requestFocus() }
  NoteCard {
    val note = when (val step = keys.step) {
      is StartOverStep.Failed -> when (step.failure) {
        WriteFailure.NothingChanged ->
          boldThenPlain(stringResource(Res.string.keys_not_erased), stringResource(Res.string.start_over_failed_detail))
        WriteFailure.KeysErased -> boldThenPlain(
          stringResource(Res.string.start_over_keys_erased),
          stringResource(Res.string.start_over_keys_erased_detail),
        )
      }
      StartOverStep.Idle, StartOverStep.Confirming, StartOverStep.Erasing -> boldThenPlain(
        stringResource(Res.string.keys_unreadable),
        pluralStringResource(Res.plurals.keys_unreadable_detail, keys.holding, keys.holding),
      )
    }
    NoteLine(note)
    GlorianaButton(
      stringResource(Res.string.start_over),
      { onIntent(SettingsIntent.AskToStartOver) },
      Modifier.focusRequester(startOverFocus),
      icon = Res.drawable.ic_restart_alt_20,
    )
  }
}

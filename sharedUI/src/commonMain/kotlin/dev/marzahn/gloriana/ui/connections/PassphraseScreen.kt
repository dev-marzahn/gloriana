package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.DetailPage
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.PageBack
import dev.marzahn.gloriana.ui.components.PageTitle
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.back_to_settings
import dev.marzahn.gloriana.ui.resources.change_failed
import dev.marzahn.gloriana.ui.resources.change_failed_detail
import dev.marzahn.gloriana.ui.resources.change_passphrase
import dev.marzahn.gloriana.ui.resources.change_passphrase_text
import dev.marzahn.gloriana.ui.resources.changing
import dev.marzahn.gloriana.ui.resources.current_passphrase
import dev.marzahn.gloriana.ui.resources.error_passphrase_wrong
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_password_20
import dev.marzahn.gloriana.ui.resources.new_passphrase
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PassphraseScreen(modifier: Modifier = Modifier) {
  val viewModel: PassphraseViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) {
    viewModel.closeRequests.collect { navigator.closeWithoutAsking(Destination.Settings, PassphraseRoute) }
  }
  PassphraseContent(state, { navigator.pop(Destination.Settings) }, viewModel::on, modifier)
}

@Composable
internal fun PassphraseContent(
  state: ChangePassphrase,
  onBack: () -> Unit,
  onIntent: (ChangeIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = GlorianaTheme.spacing
  val entry = state.entry
  DetailPage(PageBack(stringResource(Res.string.back_to_settings), onBack), modifier) {
    PageTitle(stringResource(Res.string.passphrase), intro = stringResource(Res.string.change_passphrase_text))
    GlorianaButton(
      stringResource(if (entry.busy) Res.string.changing else Res.string.change_passphrase),
      { onIntent(ChangeIntent.Change) },
      kind = ButtonKind.Primary,
      icon = Res.drawable.ic_check_20,
      enabled = !entry.busy,
    )
    if (entry.step is EntryStep.Failed) {
      NoteCard {
        NoteLine(
          boldThenPlain(stringResource(Res.string.change_failed), stringResource(Res.string.change_failed_detail)),
        )
      }
    }
    Panel(
      Res.drawable.ic_password_20,
      stringResource(Res.string.passphrase),
      listOf(
        { CurrentPassphraseRow(state, onIntent) },
        {
          NewPassphraseFields(
            entry,
            stringResource(Res.string.new_passphrase),
            NewPassphraseActions(
              onPassphrase = { onIntent(ChangeIntent.TypeNew(it)) },
              onAgain = { onIntent(ChangeIntent.TypeAgain(it)) },
              onSubmit = { onIntent(ChangeIntent.Change) },
            ),
          )
        },
      ),
    )
  }
}

@Composable
private fun CurrentPassphraseRow(state: ChangePassphrase, onIntent: (ChangeIntent) -> Unit) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(state.currentWrong) { if (state.currentWrong) focus.requestFocus() }
  val error = if (state.currentWrong) stringResource(Res.string.error_passphrase_wrong) else null
  LabelledField(stringResource(Res.string.current_passphrase), error = error) {
    SecretField(
      state.current,
      { onIntent(ChangeIntent.TypeCurrent(it)) },
      passphraseLabels(),
      Modifier.focusRequester(focus),
      enabled = !state.entry.busy,
      error = error,
    )
  }
}

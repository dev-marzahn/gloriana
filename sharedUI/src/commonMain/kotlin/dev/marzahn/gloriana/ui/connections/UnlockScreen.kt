package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.AppMark
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.navigation.ShellInsets
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.checking
import dev.marzahn.gloriana.ui.resources.damaged_no_keys
import dev.marzahn.gloriana.ui.resources.damaged_text
import dev.marzahn.gloriana.ui.resources.damaged_title
import dev.marzahn.gloriana.ui.resources.erasing
import dev.marzahn.gloriana.ui.resources.error_passphrase_wrong
import dev.marzahn.gloriana.ui.resources.forgot_passphrase
import dev.marzahn.gloriana.ui.resources.ic_lock_open_20
import dev.marzahn.gloriana.ui.resources.ic_restart_alt_20
import dev.marzahn.gloriana.ui.resources.keys_not_checked
import dev.marzahn.gloriana.ui.resources.keys_not_checked_detail
import dev.marzahn.gloriana.ui.resources.keys_not_erased
import dev.marzahn.gloriana.ui.resources.new_passphrase
import dev.marzahn.gloriana.ui.resources.no_keys_held
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.resources.reset
import dev.marzahn.gloriana.ui.resources.reset_confirm
import dev.marzahn.gloriana.ui.resources.reset_failed_detail
import dev.marzahn.gloriana.ui.resources.reset_keys_erased
import dev.marzahn.gloriana.ui.resources.reset_keys_erased_detail
import dev.marzahn.gloriana.ui.resources.reset_text
import dev.marzahn.gloriana.ui.resources.reset_title
import dev.marzahn.gloriana.ui.resources.unlock
import dev.marzahn.gloriana.ui.resources.unlock_text
import dev.marzahn.gloriana.ui.resources.unlock_title
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val MarkSize = 56.dp
private val ColumnWidth = 360.dp

class UnlockActions(val onType: (SecretText) -> Unit, val onUnlock: () -> Unit, val onOpenReset: () -> Unit)

@Composable
fun KeysGateBeforeShell(modifier: Modifier = Modifier, shell: @Composable () -> Unit) {
  val viewModel: KeysGateViewModel = koinViewModel()
  val gate by viewModel.gate.collectAsStateWithLifecycle()
  val motion = GlorianaTheme.motion
  AnimatedContent(
    targetState = gate,
    modifier = modifier,
    contentKey = { it == KeysGate.Open },
    transitionSpec = { fadeIn(motion.fadeThroughIn.tween()) togetherWith fadeOut(motion.fadeThroughOut.tween()) },
  ) { shown ->
    when (shown) {
      KeysGate.Open -> shell()
      KeysGate.Checking -> Box(Modifier.fillMaxSize().background(GlorianaTheme.colors.canvas))
      is KeysGate.Shown -> GateScreen(shown, viewModel::on)
    }
  }
}

@Composable
private fun GateScreen(gate: KeysGate.Shown, onIntent: (GateIntent) -> Unit) {
  UnlockContent(gate, onIntent)
  val asking = when (gate) {
    is KeysGate.Asking -> gate
    KeysGate.CouldNotCheck -> return
  }
  val sheet = when (val reset = asking.reset) {
    is ResetSheet.Open -> reset
    ResetSheet.Closed -> return
  }
  PassphraseSheet(
    resetSheetText(asking.holding),
    sheet.entry,
    NewPassphraseActions(
      onPassphrase = { onIntent(GateIntent.TypeNewPassphrase(it)) },
      onAgain = { onIntent(GateIntent.TypeNewPassphraseAgain(it)) },
      onSubmit = { onIntent(GateIntent.Reset) },
    ),
    ConfirmStyle.Danger,
    onCancel = { onIntent(GateIntent.CloseReset) },
  )
}

@Composable
internal fun resetSheetText(holding: Int): PassphraseSheetText {
  val nothingChanged =
    FailureText(stringResource(Res.string.keys_not_erased), stringResource(Res.string.reset_failed_detail))
  val keysErased =
    FailureText(stringResource(Res.string.reset_keys_erased), stringResource(Res.string.reset_keys_erased_detail))
  return PassphraseSheetText(
    title = stringResource(Res.string.reset_title),
    text = if (holding == 0) {
      stringResource(Res.string.no_keys_held)
    } else {
      pluralStringResource(Res.plurals.reset_text, holding, holding)
    },
    fieldLabel = stringResource(Res.string.new_passphrase),
    confirm = stringResource(Res.string.reset_confirm),
    busyConfirm = stringResource(Res.string.erasing),
    failure = { failure ->
      when (failure) {
        WriteFailure.NothingChanged -> nothingChanged
        WriteFailure.KeysErased -> keysErased
      }
    },
  )
}

@Composable
internal fun UnlockContent(gate: KeysGate.Shown, onIntent: (GateIntent) -> Unit, modifier: Modifier = Modifier) {
  val spacing = GlorianaTheme.spacing
  val compact = GlorianaTheme.widthClass.navigation == Navigation.Bar
  Box(
    modifier
      .fillMaxSize()
      .background(GlorianaTheme.colors.canvas)
      .windowInsetsPadding(ShellInsets.safeDrawingWithoutKeyboard)
      .verticalScroll(rememberScrollState())
      .padding(spacing.s24),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.widthIn(max = ColumnWidth)),
      verticalArrangement = Arrangement.spacedBy(spacing.s16),
    ) {
      AppMark(size = MarkSize)
      when (gate) {
        is KeysGate.Damaged -> DamagedPrompt(gate.holding) { onIntent(GateIntent.OpenReset) }
        is KeysGate.Unlocking -> {
          UnlockHeading()
          PassphrasePrompt(
            gate.entry,
            UnlockActions(
              onType = { onIntent(GateIntent.TypePassphrase(it)) },
              onUnlock = { onIntent(GateIntent.Unlock) },
              onOpenReset = { onIntent(GateIntent.OpenReset) },
            ),
          )
        }
        KeysGate.CouldNotCheck -> {
          UnlockHeading()
          RetryNote(stringResource(Res.string.keys_not_checked), Res.string.keys_not_checked_detail) {
            onIntent(GateIntent.Retry)
          }
        }
      }
    }
  }
}

@Composable
private fun DamagedPrompt(holding: Int, onReset: () -> Unit) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    Text(stringResource(Res.string.damaged_title), style = type.headlineSmall, color = colors.text)
    Text(damagedText(holding), style = type.bodyMedium, color = colors.textMuted)
    GlorianaButton(
      stringResource(Res.string.reset),
      onReset,
      Modifier.fillMaxWidth(),
      kind = ButtonKind.Primary,
      icon = Res.drawable.ic_restart_alt_20,
    )
  }
}

@Composable
private fun damagedText(holding: Int): String = if (holding == 0) {
  stringResource(Res.string.damaged_no_keys)
} else {
  pluralStringResource(Res.plurals.damaged_text, holding, holding)
}

@Composable
internal fun UnlockHeading(modifier: Modifier = Modifier) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    Text(stringResource(Res.string.unlock_title), style = type.headlineSmall, color = colors.text)
    Text(stringResource(Res.string.unlock_text), style = type.bodyMedium, color = colors.textMuted)
  }
}

@Composable
private fun PassphrasePrompt(entry: UnlockEntry, actions: UnlockActions) {
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    UnlockField(entry, actions)
    UnlockButton(entry, actions.onUnlock, Modifier.fillMaxWidth())
    ForgotButton(entry, actions.onOpenReset)
  }
}

@Composable
internal fun UnlockField(entry: UnlockEntry, actions: UnlockActions, modifier: Modifier = Modifier) {
  val focus = remember { FocusRequester() }
  val wrong = entry.step == UnlockStep.Wrong
  LaunchedEffect(entry.checking) { if (!entry.checking) focus.requestFocus() }
  val error = if (wrong) stringResource(Res.string.error_passphrase_wrong) else null
  LabelledField(stringResource(Res.string.passphrase), modifier, error = error) {
    SecretField(
      entry.passphrase,
      actions.onType,
      passphraseLabels(),
      Modifier.focusRequester(focus),
      enabled = !entry.checking,
      error = error,
      imeAction = ImeAction.Done,
      onDone = actions.onUnlock,
      selectAll = wrong,
    )
  }
}

@Composable
internal fun UnlockButton(entry: UnlockEntry, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
  GlorianaButton(
    stringResource(if (entry.checking) Res.string.checking else Res.string.unlock),
    onUnlock,
    modifier,
    kind = ButtonKind.Primary,
    icon = Res.drawable.ic_lock_open_20,
    enabled = !entry.checking,
  )
}

@Composable
internal fun ForgotButton(entry: UnlockEntry, onOpenReset: () -> Unit, modifier: Modifier = Modifier) {
  GlorianaButton(
    stringResource(Res.string.forgot_passphrase),
    onOpenReset,
    modifier,
    kind = ButtonKind.Ghost,
    enabled = !entry.checking,
  )
}

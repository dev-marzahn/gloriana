package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.marzahn.gloriana.logic.keys.NewPassphraseProblem
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.Confirmation
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaConfirmation
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.erasing
import dev.marzahn.gloriana.ui.resources.error_passphrase_mismatch
import dev.marzahn.gloriana.ui.resources.error_passphrase_short
import dev.marzahn.gloriana.ui.resources.hide_passphrase
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_visibility
import dev.marzahn.gloriana.ui.resources.ic_visibility_off
import dev.marzahn.gloriana.ui.resources.no_keys_held
import dev.marzahn.gloriana.ui.resources.passphrase_hint
import dev.marzahn.gloriana.ui.resources.show_passphrase
import dev.marzahn.gloriana.ui.resources.start_over_confirm
import dev.marzahn.gloriana.ui.resources.start_over_text
import dev.marzahn.gloriana.ui.resources.start_over_title
import dev.marzahn.gloriana.ui.resources.type_it_again
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

class SecretLabels(val show: String, val hide: String)

@Composable
fun passphraseLabels() = SecretLabels(
  stringResource(Res.string.show_passphrase),
  stringResource(Res.string.hide_passphrase),
)

@Composable
fun SecretField(
  value: SecretText,
  onValueChange: (SecretText) -> Unit,
  labels: SecretLabels,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  error: String? = null,
  imeAction: ImeAction = ImeAction.Next,
  onDone: () -> Unit = {},
  selectAll: Boolean = false,
  placeholder: String? = null,
) {
  var shown by rememberSaveable { mutableStateOf(false) }
  GlorianaTextField(
    value = value.text,
    onValueChange = { onValueChange(SecretText(it)) },
    selectAll = selectAll,
    modifier = modifier.fillMaxWidth(),
    enabled = enabled,
    placeholder = placeholder,
    error = error,
    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Password,
      autoCorrectEnabled = false,
      imeAction = imeAction,
    ),
    keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
    trailing = {
      GlorianaIconButton(
        if (shown) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility,
        if (shown) labels.hide else labels.show,
        { shown = !shown },
      )
    },
  )
}

@Composable
private fun NewPassphraseProblem.text(): String = stringResource(
  when (this) {
    NewPassphraseProblem.TooShort -> Res.string.error_passphrase_short
    NewPassphraseProblem.Mismatch -> Res.string.error_passphrase_mismatch
  },
)

class NewPassphraseActions(
  val onPassphrase: (SecretText) -> Unit,
  val onAgain: (SecretText) -> Unit,
  val onSubmit: () -> Unit,
)

@Composable
fun NewPassphraseFields(
  entry: NewPassphraseEntry,
  label: String,
  actions: NewPassphraseActions,
  modifier: Modifier = Modifier,
  enterSubmits: Boolean = true,
) {
  val passphraseFocus = remember { FocusRequester() }
  val againFocus = remember { FocusRequester() }
  LaunchedEffect(entry.problem) {
    when (entry.problem) {
      NewPassphraseProblem.TooShort -> passphraseFocus.requestFocus()
      NewPassphraseProblem.Mismatch -> againFocus.requestFocus()
      null -> Unit
    }
  }
  val passphraseError = entry.problem?.takeIf { it == NewPassphraseProblem.TooShort }?.text()
  val againError = entry.problem?.takeIf { it == NewPassphraseProblem.Mismatch }?.text()
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    LabelledField(label, hint = AnnotatedString(stringResource(Res.string.passphrase_hint)), error = passphraseError) {
      SecretField(
        entry.passphrase,
        actions.onPassphrase,
        passphraseLabels(),
        Modifier.focusRequester(passphraseFocus),
        enabled = !entry.busy,
        error = passphraseError,
        onDone = { againFocus.requestFocus() },
      )
    }
    LabelledField(stringResource(Res.string.type_it_again), error = againError) {
      SecretField(
        entry.again,
        actions.onAgain,
        passphraseLabels(),
        Modifier.focusRequester(againFocus),
        enabled = !entry.busy,
        error = againError,
        imeAction = if (enterSubmits) ImeAction.Done else ImeAction.Next,
        onDone = if (enterSubmits) actions.onSubmit else ({}),
      )
    }
  }
}

class FailureText(val headline: String, val detail: String)

class PassphraseSheetText(
  val title: String,
  val text: String,
  val fieldLabel: String,
  val confirm: String,
  val busyConfirm: String,
  val failure: (WriteFailure) -> FailureText,
)

enum class ConfirmStyle { Primary, Danger }

@Composable
fun PassphraseSheet(
  text: PassphraseSheetText,
  entry: NewPassphraseEntry,
  actions: NewPassphraseActions,
  confirmStyle: ConfirmStyle,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val dismiss = { if (!entry.busy) onCancel() }
  GlorianaSheet(dismiss, modifier, dismissible = !entry.busy) {
    Text(text.title, style = type.headlineSmall, color = colors.text)
    Text(
      text.text,
      Modifier.padding(top = spacing.s8, bottom = spacing.s16),
      style = type.bodyMedium,
      color = colors.textMuted,
    )
    when (val step = entry.step) {
      is EntryStep.Failed -> NoteCard(Modifier.padding(bottom = spacing.s16)) {
        val failure = text.failure(step.failure)
        NoteLine(boldThenPlain(failure.headline, failure.detail))
      }
      EntryStep.Typing, EntryStep.Busy -> Unit
    }
    NewPassphraseFields(entry, text.fieldLabel, actions, enterSubmits = confirmStyle == ConfirmStyle.Primary)
    SheetButtons(Modifier.padding(top = spacing.s24)) { buttonModifier ->
      GlorianaButton(
        if (entry.busy) text.busyConfirm else text.confirm,
        actions.onSubmit,
        buttonModifier,
        kind = if (confirmStyle == ConfirmStyle.Danger) ButtonKind.Danger else ButtonKind.Primary,
        icon = Res.drawable.ic_check_20.takeIf { confirmStyle == ConfirmStyle.Primary },
        enabled = !entry.busy,
      )
      GlorianaButton(
        stringResource(Res.string.cancel),
        dismiss,
        buttonModifier,
        kind = ButtonKind.Ghost,
        enabled = !entry.busy,
      )
    }
  }
}

@Composable
fun StartOverConfirmation(connections: Int?, erasing: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  GlorianaConfirmation(
    Confirmation(
      title = stringResource(Res.string.start_over_title),
      lines = listOfNotNull(
        when (connections) {
          null -> null
          0 -> stringResource(Res.string.no_keys_held)
          else -> pluralStringResource(Res.plurals.start_over_text, connections, connections)
        },
      ),
      confirmLabel = stringResource(if (erasing) Res.string.erasing else Res.string.start_over_confirm),
    ),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    busy = erasing,
  )
}

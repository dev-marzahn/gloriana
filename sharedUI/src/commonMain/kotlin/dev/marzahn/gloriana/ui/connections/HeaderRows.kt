package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.ui.components.AddRow
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.add_header
import dev.marzahn.gloriana.ui.resources.cancel_replace_value
import dev.marzahn.gloriana.ui.resources.enter_value
import dev.marzahn.gloriana.ui.resources.field_custom_headers
import dev.marzahn.gloriana.ui.resources.header_missing
import dev.marzahn.gloriana.ui.resources.header_name
import dev.marzahn.gloriana.ui.resources.header_unreadable_hint
import dev.marzahn.gloriana.ui.resources.header_value
import dev.marzahn.gloriana.ui.resources.headers_hint
import dev.marzahn.gloriana.ui.resources.hide_value
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.resources.ic_edit
import dev.marzahn.gloriana.ui.resources.ic_undo_20
import dev.marzahn.gloriana.ui.resources.key_missing_hint
import dev.marzahn.gloriana.ui.resources.keychain_unavailable_hint
import dev.marzahn.gloriana.ui.resources.remove_header
import dev.marzahn.gloriana.ui.resources.replace_value
import dev.marzahn.gloriana.ui.resources.show_value
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HeaderRows(page: ConnectionPageState.Shown, editing: FormEditing, modifier: Modifier = Modifier) {
  val spacing = GlorianaTheme.spacing
  Column(
    modifier.animateContentSize(GlorianaTheme.motion.standard.tween()),
    verticalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    Text(
      stringResource(Res.string.field_custom_headers),
      style = GlorianaTheme.type.material.labelMedium,
      color = GlorianaTheme.colors.textSubtle,
    )
    editing.form.headers.forEach { row -> HeaderLine(page, editing, row) }
    AddRow(stringResource(Res.string.add_header), { editing.toggle { it.withHeader() } }, density = ListDensity.Pane)
    FieldNote(stringResource(Res.string.headers_hint), null)
  }
}

private val StoredSecret.headerHint: StringResource?
  get() = when (this) {
    StoredSecret.Saved, StoredSecret.Locked -> null
    StoredSecret.Missing -> Res.string.key_missing_hint
    StoredSecret.KeychainUnavailable -> Res.string.keychain_unavailable_hint
    StoredSecret.Unreadable -> Res.string.header_unreadable_hint
  }

@Composable
private fun HeaderLine(page: ConnectionPageState.Shown, editing: FormEditing, row: HeaderRow) {
  val nameField = FormField.HeaderName(row.id)
  val valueField = FormField.HeaderValue(row.id)
  val nameError = editing.error(nameField)?.text()
  val valueError = editing.error(valueField)?.text()
  val stored = row.storedName?.let { name -> page.secrets.stored { HeaderName.of(name) in it.headers } }
  val setValue = { value: ValueEdit ->
    editing.edit(valueField) { it.withHeaderChanged(row.id) { header -> header.copy(value = value) } }
  }
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    Row(horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8), verticalAlignment = Alignment.Top) {
      HeaderNameField(editing, row, nameError)
      when (val value = row.value) {
        is ValueEdit.Typed -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          SecretField(
            value.text,
            { setValue(ValueEdit.Typed(it)) },
            SecretLabels(stringResource(Res.string.show_value), stringResource(Res.string.hide_value)),
            Modifier.weight(1f).focusRequester(editing.focus(valueField)),
            enabled = editing.enabled,
            error = valueError,
            placeholder = stringResource(Res.string.header_value),
          )
          if (stored != null) {
            GlorianaIconButton(
              Res.drawable.ic_undo_20,
              stringResource(Res.string.cancel_replace_value),
              { setValue(ValueEdit.Kept) },
              enabled = editing.enabled,
            )
          }
        }
        ValueEdit.Kept -> KeptHeaderValue(
          stored ?: StoredSecret.Saved,
          enabled = editing.enabled,
          onEnter = { setValue(ValueEdit.Typed(SecretText.Empty)) },
          enterFocus = editing.focus(valueField),
        )
      }
      GlorianaIconButton(
        Res.drawable.ic_close,
        stringResource(Res.string.remove_header),
        { editing.toggle { it.withoutHeader(row.id) } },
        enabled = editing.enabled,
      )
    }
    FieldNote(stored?.headerHint?.let { stringResource(it) }, nameError ?: valueError)
  }
}

@Composable
private fun RowScope.HeaderNameField(editing: FormEditing, row: HeaderRow, error: String?) {
  val nameField = FormField.HeaderName(row.id)
  GlorianaTextField(
    value = row.name,
    onValueChange = { text ->
      editing.edit(nameField) { it.withHeaderChanged(row.id) { header -> header.copy(name = text) } }
    },
    modifier = Modifier.weight(1f).focusRequester(editing.focus(nameField)),
    enabled = editing.enabled,
    placeholder = stringResource(Res.string.header_name),
    error = error,
    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
  )
}

@Composable
private fun RowScope.KeptHeaderValue(
  secret: StoredSecret,
  enabled: Boolean,
  onEnter: () -> Unit,
  enterFocus: FocusRequester,
) {
  Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
    SecretStatus(secret, Res.string.header_missing, Modifier.weight(1f))
    if (secret == StoredSecret.Saved || secret == StoredSecret.Missing || secret == StoredSecret.Locked) {
      GlorianaIconButton(
        Res.drawable.ic_edit,
        stringResource(if (secret == StoredSecret.Missing) Res.string.enter_value else Res.string.replace_value),
        onEnter,
        Modifier.focusRequester(enterFocus),
        enabled = enabled,
      )
    }
  }
}

package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.ui.components.AddRow
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.add_header
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.cancel_replace_value
import dev.marzahn.gloriana.ui.resources.enter_key
import dev.marzahn.gloriana.ui.resources.enter_value
import dev.marzahn.gloriana.ui.resources.field_custom_headers
import dev.marzahn.gloriana.ui.resources.field_key
import dev.marzahn.gloriana.ui.resources.header_missing
import dev.marzahn.gloriana.ui.resources.header_name
import dev.marzahn.gloriana.ui.resources.header_unreadable_hint
import dev.marzahn.gloriana.ui.resources.header_value
import dev.marzahn.gloriana.ui.resources.headers_hint
import dev.marzahn.gloriana.ui.resources.hide_key
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.resources.ic_undo_20
import dev.marzahn.gloriana.ui.resources.ic_edit
import dev.marzahn.gloriana.ui.resources.ic_key_off_20
import dev.marzahn.gloriana.ui.resources.ic_lock_20
import dev.marzahn.gloriana.ui.resources.key_hint_empty
import dev.marzahn.gloriana.ui.resources.key_hint_replacing
import dev.marzahn.gloriana.ui.resources.key_hint_saved
import dev.marzahn.gloriana.ui.resources.key_hint_typed
import dev.marzahn.gloriana.ui.resources.key_missing
import dev.marzahn.gloriana.ui.resources.key_missing_hint
import dev.marzahn.gloriana.ui.resources.key_removed
import dev.marzahn.gloriana.ui.resources.key_unreadable
import dev.marzahn.gloriana.ui.resources.key_unreadable_hint
import dev.marzahn.gloriana.ui.resources.keychain_unavailable
import dev.marzahn.gloriana.ui.resources.keychain_unavailable_hint
import dev.marzahn.gloriana.ui.resources.optional
import dev.marzahn.gloriana.ui.resources.remove
import dev.marzahn.gloriana.ui.resources.remove_header
import dev.marzahn.gloriana.ui.resources.replace
import dev.marzahn.gloriana.ui.resources.replace_value
import dev.marzahn.gloriana.ui.resources.saved_secret
import dev.marzahn.gloriana.ui.resources.show_key
import dev.marzahn.gloriana.ui.resources.undo
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val StatusIconSize = 16.dp

enum class StoredSecret { Saved, Missing, KeychainUnavailable, Unreadable }

private fun SecretsState.stored(missingHere: (SecretsState.Missing) -> Boolean): StoredSecret = when (this) {
  SecretsState.Usable -> StoredSecret.Saved
  is SecretsState.Missing -> if (missingHere(this)) StoredSecret.Missing else StoredSecret.Saved
  is SecretsState.KeychainUnavailable -> StoredSecret.KeychainUnavailable
  is SecretsState.Unreadable -> StoredSecret.Unreadable
}

private sealed interface KeyShown {
  data class Typing(val replacing: Boolean, val missing: Boolean) : KeyShown

  data class Stored(val secret: StoredSecret) : KeyShown

  data object Removed : KeyShown
}

private fun ConnectionForm.keyShown(secrets: SecretsState): KeyShown {
  val stored = secrets.stored { it.key }
  return when (key) {
    is KeyEdit.Typed -> KeyShown.Typing(replacing = keyStored, missing = keyStored && stored == StoredSecret.Missing)
    KeyEdit.Kept -> KeyShown.Stored(stored)
    KeyEdit.Removed -> KeyShown.Removed
  }
}

private val StoredSecret.keyHint: StringResource
  get() = when (this) {
    StoredSecret.Saved -> Res.string.key_hint_saved
    StoredSecret.Missing -> Res.string.key_missing_hint
    StoredSecret.KeychainUnavailable -> Res.string.keychain_unavailable_hint
    StoredSecret.Unreadable -> Res.string.key_unreadable_hint
  }

@Composable
private fun KeyShown.hint(form: ConnectionForm): String? = when (this) {
  is KeyShown.Typing -> stringResource(
    when {
      missing -> Res.string.key_missing_hint
      replacing -> Res.string.key_hint_replacing
      (form.key as? KeyEdit.Typed)?.text?.isEmpty != false -> Res.string.key_hint_empty
      else -> Res.string.key_hint_typed
    },
  )
  is KeyShown.Stored -> stringResource(secret.keyHint)
  KeyShown.Removed -> null
}

@Composable
fun KeyRow(page: ConnectionPageState.Shown, editing: FormEditing, modifier: Modifier = Modifier) {
  val shown = editing.form.keyShown(page.secrets)
  val error = editing.error(FormField.Key)?.text()
  val swap = GlorianaTheme.motion.standard
  LabelledField(
    stringResource(Res.string.field_key),
    modifier,
    hint = shown.hint(editing.form),
    error = error,
    labelEnd = stringResource(Res.string.optional),
  ) {
    AnimatedContent(
      targetState = shown,
      transitionSpec = { fadeIn(swap.tween()) togetherWith fadeOut(swap.tween()) },
      contentKey = { it::class },
    ) { state -> KeyControls(state, editing, error) }
  }
}

@Composable
private fun KeyControls(shown: KeyShown, editing: FormEditing, error: String?) {
  val setKey = { edit: KeyEdit -> editing.edit(FormField.Key) { it.copy(key = edit) } }
  when (shown) {
    is KeyShown.Typing -> Row(verticalAlignment = Alignment.CenterVertically) {
      SecretField(
        (editing.form.key as? KeyEdit.Typed)?.text?.text.orEmpty(),
        { setKey(KeyEdit.Typed(SecretText(it))) },
        SecretLabels(stringResource(Res.string.show_key), stringResource(Res.string.hide_key)),
        Modifier.weight(1f).focusRequester(editing.focus(FormField.Key)),
        enabled = editing.enabled,
        error = error,
      )
      if (shown.replacing) SmallGhost(Res.string.cancel, editing.enabled) { setKey(KeyEdit.Kept) }
    }
    is KeyShown.Stored -> Row(verticalAlignment = Alignment.CenterVertically) {
      SecretStatus(shown.secret, Res.string.key_missing, Modifier.weight(1f))
      when (shown.secret) {
        StoredSecret.Saved -> {
          SmallGhost(Res.string.replace, editing.enabled) { setKey(KeyEdit.Typed(SecretText.Empty)) }
          SmallGhost(Res.string.remove, editing.enabled) { setKey(KeyEdit.Removed) }
        }
        StoredSecret.Missing ->
          SmallGhost(Res.string.enter_key, editing.enabled) { setKey(KeyEdit.Typed(SecretText.Empty)) }
        StoredSecret.KeychainUnavailable, StoredSecret.Unreadable -> Unit
      }
    }
    KeyShown.Removed -> Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(Res.string.key_removed),
        Modifier.weight(1f),
        style = GlorianaTheme.type.material.bodyMedium,
        color = GlorianaTheme.colors.textMuted,
      )
      SmallGhost(Res.string.undo, editing.enabled) { setKey(KeyEdit.Kept) }
    }
  }
}

@Composable
private fun SmallGhost(label: StringResource, enabled: Boolean, onClick: () -> Unit) {
  GlorianaButton(stringResource(label), onClick, kind = ButtonKind.Ghost, enabled = enabled)
}

private class StatusLook(val icon: DrawableResource?, val danger: Boolean)

private val StoredSecret.look: StatusLook
  get() = when (this) {
    StoredSecret.Saved -> StatusLook(null, danger = false)
    StoredSecret.Missing -> StatusLook(Res.drawable.ic_key_off_20, danger = true)
    StoredSecret.KeychainUnavailable -> StatusLook(Res.drawable.ic_lock_20, danger = false)
    StoredSecret.Unreadable -> StatusLook(Res.drawable.ic_key_off_20, danger = true)
  }

private fun StoredSecret.text(missing: StringResource): StringResource = when (this) {
  StoredSecret.Saved -> Res.string.saved_secret
  StoredSecret.Missing -> missing
  StoredSecret.KeychainUnavailable -> Res.string.keychain_unavailable
  StoredSecret.Unreadable -> Res.string.key_unreadable
}

@Composable
private fun SecretStatus(secret: StoredSecret, missing: StringResource, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val look = secret.look
  val tint = if (look.danger) colors.dangerText else colors.textMuted
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
  ) {
    look.icon?.let { Icon(painterResource(it), null, Modifier.size(StatusIconSize), tint = tint) }
    Text(
      stringResource(secret.text(missing)),
      style = GlorianaTheme.type.material.bodyMedium,
      color = if (secret == StoredSecret.Saved) colors.text else tint,
    )
  }
}

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
    StoredSecret.Saved -> null
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
      GlorianaTextField(
        value = row.name,
        onValueChange = { text ->
          editing.edit(nameField) { it.withHeaderChanged(row.id) { header -> header.copy(name = text) } }
        },
        modifier = Modifier.weight(1f).focusRequester(editing.focus(nameField)),
        enabled = editing.enabled,
        placeholder = stringResource(Res.string.header_name),
        error = nameError,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
      )
      when (val value = row.value) {
        is ValueEdit.Typed -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          GlorianaTextField(
            value = value.text.text,
            onValueChange = { setValue(ValueEdit.Typed(SecretText(it))) },
            modifier = Modifier.weight(1f).focusRequester(editing.focus(valueField)),
            enabled = editing.enabled,
            placeholder = stringResource(Res.string.header_value),
            error = valueError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
          )
          if (stored != null) {
            GlorianaIconButton(
              Res.drawable.ic_undo_20,
              stringResource(Res.string.cancel_replace_value),
              { setValue(ValueEdit.Kept) },
            )
          }
        }
        ValueEdit.Kept -> KeptHeaderValue(stored ?: StoredSecret.Saved, editing.enabled) {
          setValue(ValueEdit.Typed(SecretText.Empty))
        }
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
private fun RowScope.KeptHeaderValue(secret: StoredSecret, enabled: Boolean, onEnter: () -> Unit) {
  Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
    SecretStatus(secret, Res.string.header_missing, Modifier.weight(1f))
    if (secret == StoredSecret.Saved || secret == StoredSecret.Missing) {
      GlorianaIconButton(
        Res.drawable.ic_edit,
        stringResource(if (secret == StoredSecret.Missing) Res.string.enter_value else Res.string.replace_value),
        onEnter,
        enabled = enabled,
      )
    }
  }
}

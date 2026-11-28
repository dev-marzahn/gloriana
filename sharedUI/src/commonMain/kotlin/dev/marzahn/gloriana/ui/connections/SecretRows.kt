package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.enter_key
import dev.marzahn.gloriana.ui.resources.field_key
import dev.marzahn.gloriana.ui.resources.hide_key
import dev.marzahn.gloriana.ui.resources.ic_key_off_20
import dev.marzahn.gloriana.ui.resources.ic_lock_20
import dev.marzahn.gloriana.ui.resources.key_hint_empty
import dev.marzahn.gloriana.ui.resources.key_hint_replacing
import dev.marzahn.gloriana.ui.resources.key_hint_saved
import dev.marzahn.gloriana.ui.resources.key_hint_typed
import dev.marzahn.gloriana.ui.resources.key_locked
import dev.marzahn.gloriana.ui.resources.key_locked_hint
import dev.marzahn.gloriana.ui.resources.key_missing
import dev.marzahn.gloriana.ui.resources.key_missing_hint
import dev.marzahn.gloriana.ui.resources.key_removed
import dev.marzahn.gloriana.ui.resources.key_unreadable
import dev.marzahn.gloriana.ui.resources.key_unreadable_hint
import dev.marzahn.gloriana.ui.resources.keychain_unavailable
import dev.marzahn.gloriana.ui.resources.keychain_unavailable_hint
import dev.marzahn.gloriana.ui.resources.optional
import dev.marzahn.gloriana.ui.resources.remove
import dev.marzahn.gloriana.ui.resources.replace
import dev.marzahn.gloriana.ui.resources.saved_secret
import dev.marzahn.gloriana.ui.resources.show_key
import dev.marzahn.gloriana.ui.resources.undo
import dev.marzahn.gloriana.ui.resources.unlock
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val StatusIconSize = 16.dp

enum class StoredSecret { Saved, Missing, Locked, KeychainUnavailable, Unreadable }

internal fun SecretsState.stored(missingHere: (SecretsState.Missing) -> Boolean): StoredSecret = when (this) {
  SecretsState.Usable -> StoredSecret.Saved
  is SecretsState.Missing -> if (missingHere(this)) StoredSecret.Missing else StoredSecret.Saved
  is SecretsState.Locked -> StoredSecret.Locked
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
    KeyEdit.Entering, is KeyEdit.Typed ->
      KeyShown.Typing(replacing = keyStored, missing = keyStored && stored == StoredSecret.Missing)
    KeyEdit.Kept -> KeyShown.Stored(stored)
    KeyEdit.Removed -> KeyShown.Removed
  }
}

private val StoredSecret.keyHint: StringResource
  get() = when (this) {
    StoredSecret.Saved -> Res.string.key_hint_saved
    StoredSecret.Missing -> Res.string.key_missing_hint
    StoredSecret.Locked -> Res.string.key_locked_hint
    StoredSecret.KeychainUnavailable -> Res.string.keychain_unavailable_hint
    StoredSecret.Unreadable -> Res.string.key_unreadable_hint
  }

@Composable
private fun KeyShown.hint(form: ConnectionForm): String? = when (this) {
  is KeyShown.Typing -> stringResource(
    when {
      missing -> Res.string.key_missing_hint
      replacing -> Res.string.key_hint_replacing
      form.key !is KeyEdit.Typed -> Res.string.key_hint_empty
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
    hint = shown.hint(editing.form)?.let(::AnnotatedString),
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
        editing.form.key.typedText,
        { setKey(KeyEdit.typing(it)) },
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
          SmallGhost(Res.string.replace, editing.enabled) { setKey(KeyEdit.Entering) }
          SmallGhost(Res.string.remove, editing.enabled) { setKey(KeyEdit.Removed) }
        }
        StoredSecret.Missing ->
          SmallGhost(Res.string.enter_key, editing.enabled) { setKey(KeyEdit.Entering) }
        StoredSecret.Locked -> SmallGhost(Res.string.unlock, editing.enabled, editing::unlock)
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
    StoredSecret.Locked -> StatusLook(Res.drawable.ic_lock_20, danger = false)
    StoredSecret.KeychainUnavailable -> StatusLook(Res.drawable.ic_lock_20, danger = false)
    StoredSecret.Unreadable -> StatusLook(Res.drawable.ic_key_off_20, danger = true)
  }

private fun StoredSecret.text(missing: StringResource): StringResource = when (this) {
  StoredSecret.Saved -> Res.string.saved_secret
  StoredSecret.Missing -> missing
  StoredSecret.Locked -> Res.string.key_locked
  StoredSecret.KeychainUnavailable -> Res.string.keychain_unavailable
  StoredSecret.Unreadable -> Res.string.key_unreadable
}

@Composable
internal fun SecretStatus(secret: StoredSecret, missing: StringResource, modifier: Modifier = Modifier) {
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

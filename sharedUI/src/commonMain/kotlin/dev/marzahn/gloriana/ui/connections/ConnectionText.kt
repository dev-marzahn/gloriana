package dev.marzahn.gloriana.ui.connections

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.ui.components.LineTone
import dev.marzahn.gloriana.ui.components.RowLine
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.format_anthropic_messages
import dev.marzahn.gloriana.ui.resources.format_openai_chat_completions
import dev.marzahn.gloriana.ui.resources.format_openai_responses
import dev.marzahn.gloriana.ui.resources.ic_key_off_20
import dev.marzahn.gloriana.ui.resources.ic_lock_20
import dev.marzahn.gloriana.ui.resources.key_missing_both
import dev.marzahn.gloriana.ui.resources.key_missing_both_short
import dev.marzahn.gloriana.ui.resources.key_missing_headers
import dev.marzahn.gloriana.ui.resources.key_missing_headers_short
import dev.marzahn.gloriana.ui.resources.key_missing_key
import dev.marzahn.gloriana.ui.resources.key_missing_key_short
import dev.marzahn.gloriana.ui.resources.key_unreadable_both
import dev.marzahn.gloriana.ui.resources.key_unreadable_both_short
import dev.marzahn.gloriana.ui.resources.key_unreadable_headers
import dev.marzahn.gloriana.ui.resources.key_unreadable_headers_short
import dev.marzahn.gloriana.ui.resources.key_unreadable_key
import dev.marzahn.gloriana.ui.resources.key_unreadable_key_short
import dev.marzahn.gloriana.ui.resources.keychain_unavailable_line
import dev.marzahn.gloriana.ui.resources.keychain_unavailable_line_short
import dev.marzahn.gloriana.ui.resources.secrets_locked_line
import dev.marzahn.gloriana.ui.resources.what_both
import dev.marzahn.gloriana.ui.resources.what_headers
import dev.marzahn.gloriana.ui.resources.what_key
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val ApiFormat.label: StringResource
  get() = when (this) {
    ApiFormat.AnthropicMessages -> Res.string.format_anthropic_messages
    ApiFormat.OpenAiChatCompletions -> Res.string.format_openai_chat_completions
    ApiFormat.OpenAiResponses -> Res.string.format_openai_responses
  }

val HeldSecrets.what: StringResource
  get() = when (this) {
    HeldSecrets.Key -> Res.string.what_key
    HeldSecrets.HeaderValues -> Res.string.what_headers
    HeldSecrets.KeyAndHeaderValues -> Res.string.what_both
  }

private class Variants(val key: StringResource, val headers: StringResource, val both: StringResource) {
  fun of(held: HeldSecrets) = when (held) {
    HeldSecrets.Key -> key
    HeldSecrets.HeaderValues -> headers
    HeldSecrets.KeyAndHeaderValues -> both
  }
}

private val missingLines =
  Variants(Res.string.key_missing_key, Res.string.key_missing_headers, Res.string.key_missing_both)
private val missingShort =
  Variants(Res.string.key_missing_key_short, Res.string.key_missing_headers_short, Res.string.key_missing_both_short)
private val unreadableLines =
  Variants(Res.string.key_unreadable_key, Res.string.key_unreadable_headers, Res.string.key_unreadable_both)
private val unreadableShort = Variants(
  Res.string.key_unreadable_key_short,
  Res.string.key_unreadable_headers_short,
  Res.string.key_unreadable_both_short,
)

@Composable
fun secretsLine(secrets: SecretsState, short: Boolean): RowLine? = when (secrets) {
  SecretsState.Usable -> null
  is SecretsState.Missing -> RowLine.Flagged(
    Res.drawable.ic_key_off_20,
    stringResource((if (short) missingShort else missingLines).of(secrets.held)),
    LineTone.Danger,
  )
  is SecretsState.Locked ->
    RowLine.Flagged(Res.drawable.ic_lock_20, stringResource(Res.string.secrets_locked_line), LineTone.Muted)
  is SecretsState.KeychainUnavailable -> RowLine.Flagged(
    Res.drawable.ic_lock_20,
    stringResource(if (short) Res.string.keychain_unavailable_line_short else Res.string.keychain_unavailable_line),
    LineTone.Muted,
  )
  is SecretsState.Unreadable -> RowLine.Flagged(
    Res.drawable.ic_key_off_20,
    stringResource((if (short) unreadableShort else unreadableLines).of(secrets.held)),
    LineTone.Danger,
  )
}

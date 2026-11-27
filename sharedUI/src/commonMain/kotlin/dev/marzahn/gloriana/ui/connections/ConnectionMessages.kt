package dev.marzahn.gloriana.ui.connections

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.JsonFault
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.character_invisible
import dev.marzahn.gloriana.ui.resources.character_line_break
import dev.marzahn.gloriana.ui.resources.character_space
import dev.marzahn.gloriana.ui.resources.character_special
import dev.marzahn.gloriana.ui.resources.delete_failed
import dev.marzahn.gloriana.ui.resources.delete_failed_detail
import dev.marzahn.gloriana.ui.resources.error_header_name_character
import dev.marzahn.gloriana.ui.resources.error_header_name_taken
import dev.marzahn.gloriana.ui.resources.error_header_reserved
import dev.marzahn.gloriana.ui.resources.error_header_value_character
import dev.marzahn.gloriana.ui.resources.error_json
import dev.marzahn.gloriana.ui.resources.error_json_unplaced
import dev.marzahn.gloriana.ui.resources.error_key_character
import dev.marzahn.gloriana.ui.resources.error_name_taken
import dev.marzahn.gloriana.ui.resources.host_said
import dev.marzahn.gloriana.ui.resources.json_brace
import dev.marzahn.gloriana.ui.resources.json_bracket
import dev.marzahn.gloriana.ui.resources.json_character
import dev.marzahn.gloriana.ui.resources.json_comma
import dev.marzahn.gloriana.ui.resources.json_other
import dev.marzahn.gloriana.ui.resources.json_quote
import dev.marzahn.gloriana.ui.resources.json_trailing
import dev.marzahn.gloriana.ui.resources.json_value
import dev.marzahn.gloriana.ui.resources.keychain_note_saved
import dev.marzahn.gloriana.ui.resources.keychain_note_saved_detail
import dev.marzahn.gloriana.ui.resources.keychain_note_sent
import dev.marzahn.gloriana.ui.resources.keychain_note_sent_detail
import dev.marzahn.gloriana.ui.resources.missing_note_both
import dev.marzahn.gloriana.ui.resources.missing_note_headers
import dev.marzahn.gloriana.ui.resources.missing_note_key
import dev.marzahn.gloriana.ui.resources.missing_note_key_detail
import dev.marzahn.gloriana.ui.resources.missing_note_values_detail
import dev.marzahn.gloriana.ui.resources.not_made_default
import dev.marzahn.gloriana.ui.resources.not_made_default_detail
import dev.marzahn.gloriana.ui.resources.not_saved
import dev.marzahn.gloriana.ui.resources.not_saved_detail
import dev.marzahn.gloriana.ui.resources.not_saved_storage
import dev.marzahn.gloriana.ui.resources.not_saved_storage_detail
import dev.marzahn.gloriana.ui.resources.reason_bad_address
import dev.marzahn.gloriana.ui.resources.reason_bad_header
import dev.marzahn.gloriana.ui.resources.reason_host_error
import dev.marzahn.gloriana.ui.resources.reason_host_error_unknown
import dev.marzahn.gloriana.ui.resources.reason_key_rejected
import dev.marzahn.gloriana.ui.resources.reason_plain_http
import dev.marzahn.gloriana.ui.resources.reason_rate_limited
import dev.marzahn.gloriana.ui.resources.reason_refused
import dev.marzahn.gloriana.ui.resources.reason_stream_not_boolean
import dev.marzahn.gloriana.ui.resources.reason_timeout
import dev.marzahn.gloriana.ui.resources.reason_unexpected
import dev.marzahn.gloriana.ui.resources.reason_unreachable
import dev.marzahn.gloriana.ui.resources.two_sentences
import dev.marzahn.gloriana.ui.resources.unreadable_note_saved
import dev.marzahn.gloriana.ui.resources.unreadable_note_saved_detail
import dev.marzahn.gloriana.ui.resources.unreadable_note_sent
import dev.marzahn.gloriana.ui.resources.unreadable_note_sent_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val LINE_BREAK = '\n'
private const val RETURN = '\r'
private const val SPACE = ' '

@Composable
fun FieldError.text(): String = when (this) {
  is FieldError.Fixed -> stringResource(message)
  is FieldError.NameTaken -> stringResource(Res.string.error_name_taken, name)
  is FieldError.KeyBadCharacter -> stringResource(Res.string.error_key_character, secretCharacterName(character))
  is FieldError.Sampler -> error.text()
  is FieldError.HeaderNameBadCharacter ->
    stringResource(Res.string.error_header_name_character, characterName(character))
  is FieldError.HeaderNameTaken -> stringResource(Res.string.error_header_name_taken, name)
  is FieldError.HeaderNameReserved -> stringResource(Res.string.error_header_reserved, name)
  is FieldError.HeaderValueBadCharacter ->
    stringResource(Res.string.error_header_value_character, secretCharacterName(character))
  is FieldError.NotJson -> jsonErrorText(this)
}

@Composable
private fun jsonErrorText(error: FieldError.NotJson): String {
  val what = stringResource(error.fault.what)
  return error.position?.let { stringResource(Res.string.error_json, what, it.line, it.column) }
    ?: stringResource(Res.string.error_json_unplaced, what)
}

private val JsonFault.what: StringResource
  get() = when (this) {
    JsonFault.CommaMissing -> Res.string.json_comma
    JsonFault.BraceMissing -> Res.string.json_brace
    JsonFault.BracketMissing -> Res.string.json_bracket
    JsonFault.QuoteMissing -> Res.string.json_quote
    JsonFault.ValueMissing -> Res.string.json_value
    JsonFault.CharacterNotAllowed -> Res.string.json_character
    JsonFault.TextAfterTheEnd -> Res.string.json_trailing
    JsonFault.Other -> Res.string.json_other
  }

@Composable
private fun characterName(character: Char): String =
  characterClass(character)?.let { stringResource(it) } ?: character.toString()

@Composable
private fun secretCharacterName(character: Char): String =
  stringResource(characterClass(character) ?: Res.string.character_special)

private fun characterClass(character: Char): StringResource? = when {
  character == SPACE -> Res.string.character_space
  character == LINE_BREAK || character == RETURN -> Res.string.character_line_break
  character.isISOControl() || character.isWhitespace() || !character.isDefined() -> Res.string.character_invisible
  else -> null
}

@Composable
fun HostFailure.reason(): String = when (val failed = problem) {
  HostProblem.Unreachable -> stringResource(Res.string.reason_unreachable)
  is HostProblem.Timeout -> stringResource(Res.string.reason_timeout, failed.seconds)
  is HostProblem.KeyRejected -> stringResource(
    if (keys == KeyContext.SavedKey) Res.string.reason_key_rejected else Res.string.reason_refused,
    failed.status,
  )
  is HostProblem.RateLimited -> stringResource(Res.string.reason_rate_limited)
  is HostProblem.HostError -> failed.status?.let { stringResource(Res.string.reason_host_error, it.toString()) }
    ?: stringResource(Res.string.reason_host_error_unknown)
  HostProblem.PlainHttpToPublicHost -> stringResource(Res.string.reason_plain_http)
  HostProblem.BadAddress -> stringResource(Res.string.reason_bad_address)
  HostProblem.BadHeader -> stringResource(Res.string.reason_bad_header)
  HostProblem.StreamNotBoolean -> stringResource(Res.string.reason_stream_not_boolean)
  HostProblem.Unexpected -> stringResource(Res.string.reason_unexpected)
}

@Composable
fun HostFailure.details(use: StepFor): String {
  val step = stringResource(step(use))
  val said = when (val failed = problem) {
    is HostProblem.WithBody -> failed.body
    HostProblem.Unreachable, is HostProblem.Timeout, HostProblem.PlainHttpToPublicHost, HostProblem.BadAddress,
    HostProblem.BadHeader, HostProblem.StreamNotBoolean, HostProblem.Unexpected,
    -> ""
  }
  return if (said.isBlank()) {
    step
  } else {
    stringResource(Res.string.two_sentences, stringResource(Res.string.host_said, said), step)
  }
}

@Composable
fun PageNote.text(name: String): AnnotatedString = when (this) {
  is PageNote.BlockedSend -> text()
  is PageNote.KeysBlockedSave -> when (problem) {
    KeysProblem.Unreadable -> boldThenPlain(
      stringResource(Res.string.unreadable_note_saved, name),
      stringResource(Res.string.unreadable_note_saved_detail),
    )
    KeysProblem.KeychainUnavailable -> boldThenPlain(
      stringResource(Res.string.keychain_note_saved, name),
      stringResource(Res.string.keychain_note_saved_detail),
    )
  }
  PageNote.NotSavedStorage -> boldThenPlain(
    stringResource(Res.string.not_saved_storage, name),
    stringResource(Res.string.not_saved_storage_detail),
  )
  PageNote.NotSaved -> boldThenPlain(
    stringResource(Res.string.not_saved, name),
    stringResource(Res.string.not_saved_detail),
  )
  PageNote.NotDeleted ->
    boldThenPlain(stringResource(Res.string.delete_failed, name), stringResource(Res.string.delete_failed_detail))
  PageNote.NotMadeDefault ->
    boldThenPlain(stringResource(Res.string.not_made_default, name), stringResource(Res.string.not_made_default_detail))
}

@Composable
fun PageNote.BlockedSend.text(): AnnotatedString = when (this) {
  is PageNote.KeysBlockedSend -> when (problem) {
    KeysProblem.KeychainUnavailable ->
      boldThenPlain(stringResource(Res.string.keychain_note_sent), stringResource(Res.string.keychain_note_sent_detail))
    KeysProblem.Unreadable -> boldThenPlain(
      stringResource(Res.string.unreadable_note_sent),
      stringResource(Res.string.unreadable_note_sent_detail, stringResource(held.what)),
    )
  }
  is PageNote.KeysMissing -> missingText(held)
}

@Composable
private fun missingText(held: HeldSecrets): AnnotatedString = when (held) {
  HeldSecrets.Key ->
    boldThenPlain(stringResource(Res.string.missing_note_key), stringResource(Res.string.missing_note_key_detail))
  HeldSecrets.HeaderValues ->
    boldThenPlain(
      stringResource(Res.string.missing_note_headers),
      stringResource(Res.string.missing_note_values_detail),
    )
  HeldSecrets.KeyAndHeaderValues ->
    boldThenPlain(stringResource(Res.string.missing_note_both), stringResource(Res.string.missing_note_values_detail))
}

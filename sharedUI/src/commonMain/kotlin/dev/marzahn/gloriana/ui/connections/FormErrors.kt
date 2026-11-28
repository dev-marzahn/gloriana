package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.JsonFault
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.TextPosition
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.error_base_url_character
import dev.marzahn.gloriana.ui.resources.error_base_url_host
import dev.marzahn.gloriana.ui.resources.error_base_url_port
import dev.marzahn.gloriana.ui.resources.error_base_url_query
import dev.marzahn.gloriana.ui.resources.error_base_url_scheme
import dev.marzahn.gloriana.ui.resources.error_base_url_user
import dev.marzahn.gloriana.ui.resources.error_below_context
import dev.marzahn.gloriana.ui.resources.error_content_type
import dev.marzahn.gloriana.ui.resources.error_format_empty
import dev.marzahn.gloriana.ui.resources.error_header_name_empty
import dev.marzahn.gloriana.ui.resources.error_header_value_enter
import dev.marzahn.gloriana.ui.resources.error_json_object
import dev.marzahn.gloriana.ui.resources.error_key_blank
import dev.marzahn.gloriana.ui.resources.error_model_empty
import dev.marzahn.gloriana.ui.resources.error_name_empty
import dev.marzahn.gloriana.ui.resources.error_stream_boolean
import dev.marzahn.gloriana.ui.resources.error_whole_above_zero
import dev.marzahn.gloriana.ui.resources.key_missing_hint
import org.jetbrains.compose.resources.StringResource

sealed interface FormField {
  data object Name : FormField

  data object Format : FormField

  data object BaseUrl : FormField

  data object Key : FormField

  data object Model : FormField

  data object ContextSize : FormField

  data object MaxReplyTokens : FormField

  data class SamplerField(val sampler: Sampler) : FormField

  data object Timeout : FormField

  data class HeaderName(val rowId: Int) : FormField

  data class HeaderValue(val rowId: Int) : FormField

  data object CustomBody : FormField
}

sealed interface FieldError {
  sealed class Fixed(val message: StringResource) : FieldError

  data object NameEmpty : Fixed(Res.string.error_name_empty)

  data object FormatEmpty : Fixed(Res.string.error_format_empty)

  data object BaseUrlNotHttp : Fixed(Res.string.error_base_url_scheme)

  data object BaseUrlNoHost : Fixed(Res.string.error_base_url_host)

  data object BaseUrlQueryOrFragment : Fixed(Res.string.error_base_url_query)

  data object BaseUrlBadPort : Fixed(Res.string.error_base_url_port)

  data object BaseUrlUserInfo : Fixed(Res.string.error_base_url_user)

  data object BaseUrlBadCharacter : Fixed(Res.string.error_base_url_character)

  data object KeyMissing : Fixed(Res.string.key_missing_hint)

  data object KeyBlank : Fixed(Res.string.error_key_blank)

  data object ModelEmpty : Fixed(Res.string.error_model_empty)

  data object WholeAboveZero : Fixed(Res.string.error_whole_above_zero)

  data object NotBelowContextSize : Fixed(Res.string.error_below_context)

  data object HeaderNameEmpty : Fixed(Res.string.error_header_name_empty)

  data object ContentTypeInvalid : Fixed(Res.string.error_content_type)

  data object HeaderValueMissing : Fixed(Res.string.error_header_value_enter)

  data object NotAnObject : Fixed(Res.string.error_json_object)

  data object StreamNotBoolean : Fixed(Res.string.error_stream_boolean)

  data class NameTaken(val name: String) : FieldError

  data class KeyBadCharacter(val character: Char) : FieldError

  data class Sampler(val error: SamplerError) : FieldError

  data class HeaderNameBadCharacter(val character: Char) : FieldError

  data class HeaderNameTaken(val name: String) : FieldError

  data class HeaderNameReserved(val name: String) : FieldError

  data class HeaderValueBadCharacter(val character: Char) : FieldError

  data class NotJson(val fault: JsonFault, val position: TextPosition?) : FieldError
}

data class FormErrors(val fields: Map<FormField, FieldError> = emptyMap()) {
  operator fun get(field: FormField): FieldError? = fields[field]

  operator fun plus(other: FormErrors) = FormErrors(fields + other.fields)

  fun isEmpty(): Boolean = fields.isEmpty()

  fun without(field: FormField) = if (field in fields) FormErrors(fields - field) else this

  fun firstIn(order: List<FormField>): FormField? = order.firstOrNull { it in fields }

  companion object {
    val None = FormErrors()
  }
}

fun ConnectionForm.fieldOrder(): List<FormField> =
  listOf(FormField.Name, FormField.Format, FormField.BaseUrl, FormField.Key) +
    listOf(FormField.Model, FormField.ContextSize, FormField.MaxReplyTokens) +
    Sampler.entries.map(FormField::SamplerField) +
    FormField.Timeout +
    headers.flatMap { listOf(FormField.HeaderName(it.id), FormField.HeaderValue(it.id)) } +
    FormField.CustomBody

internal class FieldProblem(val field: FormField, val error: FieldError)

private infix fun FormField.shows(error: FieldError) = FieldProblem(this, error)

@Suppress("CyclomaticComplexMethod")
internal fun ConnectionForm.fieldError(problem: ConnectionProblem): FieldProblem? = when (problem) {
  ConnectionProblem.NameEmpty -> FormField.Name shows FieldError.NameEmpty
  ConnectionProblem.NameTaken -> FormField.Name shows FieldError.NameTaken(name.trim())
  ConnectionProblem.ModelEmpty -> FormField.Model shows FieldError.ModelEmpty
  ConnectionProblem.BaseUrlNotHttp -> FormField.BaseUrl shows FieldError.BaseUrlNotHttp
  ConnectionProblem.BaseUrlNoHost -> FormField.BaseUrl shows FieldError.BaseUrlNoHost
  ConnectionProblem.BaseUrlBadPort -> FormField.BaseUrl shows FieldError.BaseUrlBadPort
  ConnectionProblem.BaseUrlHasUserInfo -> FormField.BaseUrl shows FieldError.BaseUrlUserInfo
  ConnectionProblem.BaseUrlHasQuery, ConnectionProblem.BaseUrlHasFragment ->
    FormField.BaseUrl shows FieldError.BaseUrlQueryOrFragment
  ConnectionProblem.BaseUrlBadCharacter -> FormField.BaseUrl shows FieldError.BaseUrlBadCharacter
  ConnectionProblem.ContextSizeNotPositive -> FormField.ContextSize shows FieldError.WholeAboveZero
  ConnectionProblem.MaxReplyTokensNotPositive -> FormField.MaxReplyTokens shows FieldError.WholeAboveZero
  ConnectionProblem.MaxReplyTokensNotBelowContextSize -> FormField.MaxReplyTokens shows FieldError.NotBelowContextSize
  ConnectionProblem.TimeoutNotPositive -> FormField.Timeout shows FieldError.WholeAboveZero
  is ConnectionProblem.SamplerOutOfRange ->
    FormField.SamplerField(problem.sampler) shows FieldError.Sampler(SamplerError.OutOfRange(problem.range))
  is ConnectionProblem.SamplerNotFinite ->
    FormField.SamplerField(problem.sampler) shows FieldError.Sampler(SamplerError.NotANumber)
  is ConnectionProblem.KeyBadCharacter -> FormField.Key shows FieldError.KeyBadCharacter(problem.character)
  ConnectionProblem.KeyMissing -> FormField.Key shows FieldError.KeyMissing
  ConnectionProblem.KeyBlank -> FormField.Key shows FieldError.KeyBlank
  ConnectionProblem.StreamNotBoolean -> FormField.CustomBody shows FieldError.StreamNotBoolean
  is ConnectionProblem.HeaderNameEmpty -> headerName(problem.index) { FieldError.HeaderNameEmpty }
  is ConnectionProblem.HeaderNameNotToken ->
    headerName(problem.index) { FieldError.HeaderNameBadCharacter(problem.character) }
  is ConnectionProblem.HeaderNameRepeated -> headerName(problem.index) { FieldError.HeaderNameTaken(it.name) }
  is ConnectionProblem.HeaderNameReserved -> headerName(problem.index) { FieldError.HeaderNameReserved(it.name) }
  is ConnectionProblem.HeaderValueBadCharacter ->
    headerValue(problem.index) { FieldError.HeaderValueBadCharacter(problem.character) }
  is ConnectionProblem.HeaderContentTypeInvalid -> headerValue(problem.index) { FieldError.ContentTypeInvalid }
  is ConnectionProblem.HeaderValueGone -> headerValue(problem.index) { FieldError.HeaderValueMissing }
  is ConnectionProblem.HeaderValueMissing -> headerValue(problem.index) { FieldError.HeaderValueMissing }
}

private fun ConnectionForm.headerName(index: Int, error: (HeaderRow) -> FieldError) =
  keptHeaders.getOrNull(index)?.let { row -> FormField.HeaderName(row.id) shows error(row) }

private fun ConnectionForm.headerValue(index: Int, error: (HeaderRow) -> FieldError) =
  keptHeaders.getOrNull(index)?.let { row -> FormField.HeaderValue(row.id) shows error(row) }

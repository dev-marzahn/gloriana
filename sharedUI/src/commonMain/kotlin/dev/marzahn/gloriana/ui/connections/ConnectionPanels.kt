package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.api.isPlainHttp
import dev.marzahn.gloriana.logic.connection.api.requestAddress
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.GlorianaDropdownField
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.InfoLine
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.PanelRow
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.base_url_hint
import dev.marzahn.gloriana.ui.resources.base_url_hint_no_format
import dev.marzahn.gloriana.ui.resources.field_api_format
import dev.marzahn.gloriana.ui.resources.field_base_url
import dev.marzahn.gloriana.ui.resources.field_context_size
import dev.marzahn.gloriana.ui.resources.field_max_reply_tokens
import dev.marzahn.gloriana.ui.resources.field_model
import dev.marzahn.gloriana.ui.resources.field_name
import dev.marzahn.gloriana.ui.resources.ic_list
import dev.marzahn.gloriana.ui.resources.ic_neurology_20
import dev.marzahn.gloriana.ui.resources.ic_send_20
import dev.marzahn.gloriana.ui.resources.ic_settings_ethernet_20
import dev.marzahn.gloriana.ui.resources.ic_tune_20
import dev.marzahn.gloriana.ui.resources.model_list_failed_field
import dev.marzahn.gloriana.ui.resources.model_list_none
import dev.marzahn.gloriana.ui.resources.model_placeholder
import dev.marzahn.gloriana.ui.resources.pick_api_format
import dev.marzahn.gloriana.ui.resources.pick_model
import dev.marzahn.gloriana.ui.resources.plain_http_note
import dev.marzahn.gloriana.ui.resources.prompt_budget
import dev.marzahn.gloriana.ui.resources.section_model
import dev.marzahn.gloriana.ui.resources.section_request
import dev.marzahn.gloriana.ui.resources.section_samplers
import dev.marzahn.gloriana.ui.resources.section_where_requests_go
import dev.marzahn.gloriana.ui.resources.two_sentences
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConnectionPanels(page: ConnectionPageState.Shown, editing: FormEditing, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    Panel(Res.drawable.ic_send_20, stringResource(Res.string.section_where_requests_go), whereRows(page, editing))
    Panel(Res.drawable.ic_neurology_20, stringResource(Res.string.section_model), modelRows(editing))
    Panel(Res.drawable.ic_tune_20, stringResource(Res.string.section_samplers), listOf { SamplerRows(page, editing) })
    Panel(Res.drawable.ic_settings_ethernet_20, stringResource(Res.string.section_request), requestRows(page, editing))
  }
}

@Composable
private fun TextRow(
  editing: FormEditing,
  field: FormField,
  label: String,
  value: String,
  onChange: (ConnectionForm, String) -> ConnectionForm,
  modifier: Modifier = Modifier,
  hint: String? = null,
  placeholder: String? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  trailing: (@Composable () -> Unit)? = null,
) {
  val error = editing.error(field)?.text()
  LabelledField(label, modifier, hint = hint, error = error) {
    GlorianaTextField(
      value = value,
      onValueChange = { text -> editing.edit(field) { onChange(it, text) } },
      modifier = Modifier.fillMaxWidth().focusRequester(editing.focus(field)),
      enabled = editing.enabled,
      placeholder = placeholder,
      error = error,
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      trailing = trailing,
    )
  }
}

private fun whereRows(page: ConnectionPageState.Shown, editing: FormEditing): List<PanelRow> = listOf(
  {
    TextRow(editing, FormField.Name, stringResource(Res.string.field_name), editing.form.name, { form, text ->
      form.copy(name = text)
    })
  },
  { FormatRow(editing) },
  { BaseUrlRow(editing) },
  { KeyRow(page, editing) },
)

@Composable
private fun FormatRow(editing: FormEditing) {
  val error = editing.error(FormField.Format)?.text()
  LabelledField(stringResource(Res.string.field_api_format), error = error) {
    GlorianaDropdownField(
      selected = editing.form.format,
      options = ApiFormat.entries,
      label = { stringResource(it.label) },
      onSelect = { format -> editing.edit(FormField.Format) { it.copy(format = format) } },
      placeholder = stringResource(Res.string.pick_api_format),
      modifier = Modifier.focusRequester(editing.focus(FormField.Format)),
      error = error,
      enabled = editing.enabled,
    )
  }
}

@Composable
private fun BaseUrlRow(editing: FormEditing) {
  val form = editing.form
  val hint = form.format?.let { stringResource(Res.string.base_url_hint, it.requestAddress(form.baseUrl)) }
    ?: stringResource(Res.string.base_url_hint_no_format)
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    TextRow(
      editing,
      FormField.BaseUrl,
      stringResource(Res.string.field_base_url),
      form.baseUrl,
      { current, text -> current.copy(baseUrl = text) },
      hint = hint,
      keyboardType = KeyboardType.Uri,
    )
    if (isPlainHttp(form.baseUrl)) InfoLine(stringResource(Res.string.plain_http_note))
  }
}

private fun modelRows(editing: FormEditing): List<PanelRow> = listOf(
  { ModelRow(editing) },
  { TokenRows(editing) },
)

@Composable
private fun ModelRow(editing: FormEditing) {
  val note = when (val listNote = editing.page.modelListNote) {
    ModelListNote.NoneListed -> stringResource(Res.string.model_list_none)
    is ModelListNote.Failed -> stringResource(
      Res.string.two_sentences,
      stringResource(Res.string.model_list_failed_field, listNote.failure.reason()),
      listNote.failure.details(StepFor.ModelField),
    )
    null -> null
  }
  TextRow(
    editing,
    FormField.Model,
    stringResource(Res.string.field_model),
    editing.form.model,
    { form, text -> form.copy(model = text) },
    hint = note,
    placeholder = stringResource(Res.string.model_placeholder),
    trailing = {
      GlorianaIconButton(
        Res.drawable.ic_list,
        stringResource(Res.string.pick_model),
        editing::openModelList,
        enabled = editing.enabled,
      )
    },
  )
}

@Composable
private fun TokenRows(editing: FormEditing) {
  val form = editing.form
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    Row(horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s12)) {
      TextRow(
        editing,
        FormField.ContextSize,
        stringResource(Res.string.field_context_size),
        form.contextSize,
        { current, text -> current.copy(contextSize = text) },
        Modifier.weight(1f),
        keyboardType = KeyboardType.Number,
      )
      TextRow(
        editing,
        FormField.MaxReplyTokens,
        stringResource(Res.string.field_max_reply_tokens),
        form.maxReplyTokens,
        { current, text -> current.copy(maxReplyTokens = text) },
        Modifier.weight(1f),
        keyboardType = KeyboardType.Number,
      )
    }
    form.promptBudget?.let { FieldNote(stringResource(Res.string.prompt_budget, it.toString()), null) }
  }
}

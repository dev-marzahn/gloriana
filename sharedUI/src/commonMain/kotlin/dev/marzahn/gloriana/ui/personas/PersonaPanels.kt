package dev.marzahn.gloriana.ui.personas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.PanelRow
import dev.marzahn.gloriana.ui.components.textWithCode
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.field_description
import dev.marzahn.gloriana.ui.resources.field_name
import dev.marzahn.gloriana.ui.resources.field_pronouns
import dev.marzahn.gloriana.ui.resources.ic_description
import dev.marzahn.gloriana.ui.resources.ic_lock
import dev.marzahn.gloriana.ui.resources.optional
import dev.marzahn.gloriana.ui.resources.persona_description_hint
import dev.marzahn.gloriana.ui.resources.persona_field_label
import dev.marzahn.gloriana.ui.resources.persona_field_note
import dev.marzahn.gloriana.ui.resources.persona_label_hint
import dev.marzahn.gloriana.ui.resources.persona_name_hint
import dev.marzahn.gloriana.ui.resources.persona_no_name
import dev.marzahn.gloriana.ui.resources.persona_not_set
import dev.marzahn.gloriana.ui.resources.persona_note_hint
import dev.marzahn.gloriana.ui.resources.persona_panel_private
import dev.marzahn.gloriana.ui.resources.persona_panel_sent
import dev.marzahn.gloriana.ui.resources.persona_pronouns_hint
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val DESCRIPTION_LINES = 3

private const val NAME_PLACEHOLDER = "{{user}}"

@Composable
internal fun PersonaPanels(page: PersonaPageState.Shown, editing: PersonaFieldEditing?) {
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s24)) {
    if (editing == null) ReadPanels(page.fields) else EditingPanels(page, editing)
  }
}

class PersonaFieldEditing(val nameFocus: FocusRequester, val onIntent: (PersonaIntent) -> Unit)

@Composable
private fun ReadPanels(fields: PersonaFields) {
  val type = GlorianaTheme.type
  val colors = GlorianaTheme.colors
  Panel(
    Res.drawable.ic_description,
    stringResource(Res.string.persona_panel_sent),
    listOf(
      { ValueRow(Res.string.field_name, fields.name, type.material.bodyLarge) },
      { ValueRow(Res.string.field_description, fields.description, type.reading) },
      { ValueRow(Res.string.field_pronouns, "", type.material.bodyLarge) },
    ),
  )
  Panel(
    Res.drawable.ic_lock,
    stringResource(Res.string.persona_panel_private),
    listOf(
      { ValueRow(Res.string.persona_field_label, fields.personaLabel, type.material.bodyLarge) },
      { ValueRow(Res.string.persona_field_note, fields.personaNote, type.material.bodyMedium, colors.textMuted) },
    ),
  )
}

@Composable
private fun ValueRow(label: StringResource, value: String, style: TextStyle, color: Color = GlorianaTheme.colors.text) {
  val colors = GlorianaTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    Text(stringResource(label), style = GlorianaTheme.type.material.labelMedium, color = colors.textSubtle)
    if (value.isBlank()) {
      Text(
        stringResource(Res.string.persona_not_set),
        style = GlorianaTheme.type.material.bodyMedium,
        color = colors.textSubtle,
      )
    } else {
      Text(value, style = style, color = color)
    }
  }
}

@Composable
private fun EditingPanels(page: PersonaPageState.Shown, editing: PersonaFieldEditing) {
  val fields = page.fields
  val enabled = !page.session.isSaving
  val onIntent = editing.onIntent
  LaunchedEffect(editing.nameFocus) { editing.nameFocus.requestFocus() }
  Panel(
    Res.drawable.ic_description,
    stringResource(Res.string.persona_panel_sent),
    listOf(
      { NameField(fields.name, page.session.nameMissing, enabled, editing) },
      {
        OptionalField(Res.string.field_description, Res.string.persona_description_hint) {
          GlorianaTextField(
            fields.description,
            { onIntent(PersonaIntent.SetDescription(it)) },
            Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = false,
            minLines = DESCRIPTION_LINES,
            textStyle = GlorianaTheme.type.reading,
          )
        }
      },
      {
        OptionalField(Res.string.field_pronouns, Res.string.persona_pronouns_hint) {
          GlorianaTextField(
            "",
            {},
            Modifier.fillMaxWidth(),
            enabled = false,
            placeholder = stringResource(Res.string.persona_not_set),
          )
        }
      },
    ),
  )
  Panel(
    Res.drawable.ic_lock,
    stringResource(Res.string.persona_panel_private),
    listOf(
      textRow(Res.string.persona_field_label, Res.string.persona_label_hint, fields.personaLabel, enabled) {
        onIntent(PersonaIntent.SetLabel(it))
      },
      textRow(
        Res.string.persona_field_note,
        Res.string.persona_note_hint,
        fields.personaNote,
        enabled,
        singleLine = false,
      ) { onIntent(PersonaIntent.SetNote(it)) },
    ),
  )
}

@Composable
private fun NameField(name: String, missing: Boolean, enabled: Boolean, editing: PersonaFieldEditing) {
  val error = if (missing) stringResource(Res.string.persona_no_name) else null
  LabelledField(
    stringResource(Res.string.field_name),
    hint = textWithCode(Res.string.persona_name_hint, NAME_PLACEHOLDER),
    error = error,
  ) {
    GlorianaTextField(
      name,
      { editing.onIntent(PersonaIntent.SetName(it)) },
      Modifier.fillMaxWidth().focusRequester(editing.nameFocus),
      enabled = enabled,
      error = error,
    )
  }
}

private fun textRow(
  label: StringResource,
  hint: StringResource,
  value: String,
  enabled: Boolean,
  singleLine: Boolean = true,
  onChange: (String) -> Unit,
): PanelRow = {
  OptionalField(label, hint) {
    GlorianaTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = singleLine)
  }
}

@Composable
private fun OptionalField(label: StringResource, hint: StringResource, field: @Composable () -> Unit) {
  LabelledField(
    stringResource(label),
    hint = AnnotatedString(stringResource(hint)),
    labelEnd = stringResource(Res.string.optional),
    field = field,
  )
}

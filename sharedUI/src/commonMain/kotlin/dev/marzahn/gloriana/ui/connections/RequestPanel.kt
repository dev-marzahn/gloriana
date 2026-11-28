package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.marzahn.gloriana.ui.components.GlorianaSwitch
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.PanelRow
import dev.marzahn.gloriana.ui.components.SettingRow
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.custom_body_hint
import dev.marzahn.gloriana.ui.resources.field_custom_body
import dev.marzahn.gloriana.ui.resources.field_include_usage
import dev.marzahn.gloriana.ui.resources.field_streaming
import dev.marzahn.gloriana.ui.resources.field_timeout
import dev.marzahn.gloriana.ui.resources.include_usage_hint
import dev.marzahn.gloriana.ui.resources.seconds
import dev.marzahn.gloriana.ui.resources.streaming_hint
import dev.marzahn.gloriana.ui.resources.timeout_hint
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

private val TimeoutFieldWidth = 140.dp
private val CodeFontSize = 14.sp
private const val CUSTOM_BODY_LINES = 4

internal fun requestRows(page: ConnectionPageState.Shown, editing: FormEditing): List<PanelRow> = listOf(
  { StreamingRows(editing) },
  { TimeoutRow(editing) },
  { HeaderRows(page, editing) },
  { CustomBodyRow(editing) },
)

@Composable
private fun StreamingRows(editing: FormEditing) {
  val form = editing.form
  Column(
    Modifier.animateContentSize(GlorianaTheme.motion.standard.tween()),
    verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s14),
  ) {
    SettingRow(stringResource(Res.string.field_streaming), stringResource(Res.string.streaming_hint), null) {
      GlorianaSwitch(form.streaming, { on -> editing.toggle { it.copy(streaming = on) } }, enabled = editing.enabled)
    }
    if (form.streaming && form.format?.reportsUsageOnRequest == true) {
      SettingRow(stringResource(Res.string.field_include_usage), stringResource(Res.string.include_usage_hint), null) {
        GlorianaSwitch(
          form.includeUsage,
          { on -> editing.toggle { it.copy(includeUsage = on) } },
          enabled = editing.enabled,
        )
      }
    }
  }
}

@Composable
private fun TimeoutRow(editing: FormEditing) {
  val error = editing.error(FormField.Timeout)?.text()
  LabelledField(
    stringResource(Res.string.field_timeout),
    hint = stringResource(Res.string.timeout_hint),
    error = error,
  ) {
    GlorianaTextField(
      value = editing.form.timeout,
      onValueChange = { text -> editing.edit(FormField.Timeout) { it.copy(timeout = text) } },
      modifier = Modifier.width(TimeoutFieldWidth).focusRequester(editing.focus(FormField.Timeout)),
      enabled = editing.enabled,
      error = error,
      suffix = stringResource(Res.string.seconds),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
  }
}

@Composable
private fun CustomBodyRow(editing: FormEditing) {
  val error = editing.error(FormField.CustomBody)?.text()
  LabelledField(
    stringResource(Res.string.field_custom_body),
    hint = stringResource(Res.string.custom_body_hint),
    error = error,
  ) {
    GlorianaTextField(
      value = editing.form.customBody,
      onValueChange = { text -> editing.edit(FormField.CustomBody) { it.copy(customBody = text) } },
      modifier = Modifier.fillMaxWidth().focusRequester(editing.focus(FormField.CustomBody)),
      enabled = editing.enabled,
      error = error,
      singleLine = false,
      minLines = CUSTOM_BODY_LINES,
      textStyle = GlorianaTheme.type.material.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = CodeFontSize,
      ),
    )
  }
}

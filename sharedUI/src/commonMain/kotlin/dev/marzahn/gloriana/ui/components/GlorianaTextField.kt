package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_error_20
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.disabled
import dev.marzahn.gloriana.ui.theme.disabledUnless
import org.jetbrains.compose.resources.painterResource

private val ErrorOutline = 2.dp
private val RestOutline = 1.dp
private val NoteIcon = 16.dp
private const val SELECTION_ALPHA = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlorianaTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  placeholder: String? = null,
  error: String? = null,
  singleLine: Boolean = true,
  minLines: Int = 1,
  textStyle: TextStyle = GlorianaTheme.type.material.bodyLarge,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  suffix: String? = null,
  trailing: (@Composable () -> Unit)? = null,
  selectAll: Boolean = false,
) {
  val colors = GlorianaTheme.colors
  val interactionSource = remember { MutableInteractionSource() }
  val fieldColors = glorianaFieldColors()
  val isError = error != null
  val shape = GlorianaTheme.shapes.control
  var edited by remember { mutableStateOf(TextFieldValue(value)) }
  val shown = if (edited.text == value) edited else TextFieldValue(value, TextRange(value.length))
  LaunchedEffect(selectAll) { if (selectAll) edited = TextFieldValue(value, TextRange(0, value.length)) }
  val selection = TextSelectionColors(colors.accent, colors.accent.copy(alpha = SELECTION_ALPHA))
  CompositionLocalProvider(LocalTextSelectionColors provides selection) {
  BasicTextField(
    value = shown,
    onValueChange = { changed ->
      edited = changed
      if (changed.text != value) onValueChange(changed.text)
    },
    modifier = modifier.semantics { if (error != null) error(error) },
    enabled = enabled,
    readOnly = readOnly,
    textStyle = textStyle.copy(color = colors.text.disabledUnless(enabled)),
    cursorBrush = SolidColor(if (isError) colors.dangerText else colors.accent),
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    interactionSource = interactionSource,
    singleLine = singleLine,
    minLines = minLines,
    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
  ) { innerTextField ->
    OutlinedTextFieldDefaults.DecorationBox(
      value = value,
      innerTextField = innerTextField,
      enabled = enabled,
      singleLine = singleLine,
      visualTransformation = visualTransformation,
      interactionSource = interactionSource,
      isError = isError,
      placeholder = placeholder?.let { { Text(it, style = textStyle) } },
      suffix = suffix?.let { { Text(it, style = textStyle) } },
      trailingIcon = trailing,
      colors = fieldColors,
      container = {
        OutlinedTextFieldDefaults.Container(
          enabled = enabled,
          isError = isError,
          interactionSource = interactionSource,
          colors = fieldColors,
          shape = shape,
          focusedBorderThickness = ErrorOutline,
          unfocusedBorderThickness = if (isError) ErrorOutline else RestOutline,
        )
      },
    )
  }
  }
}

@Composable
private fun glorianaFieldColors(): TextFieldColors {
  val colors = GlorianaTheme.colors
  return OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.text,
    unfocusedTextColor = colors.text,
    disabledTextColor = colors.text.disabled(),
    errorTextColor = colors.text,
    focusedContainerColor = colors.raised,
    unfocusedContainerColor = colors.raised,
    disabledContainerColor = colors.raised,
    errorContainerColor = colors.raised,
    cursorColor = colors.accent,
    errorCursorColor = colors.dangerText,
    focusedBorderColor = colors.accent,
    unfocusedBorderColor = colors.borderStrong,
    disabledBorderColor = colors.borderStrong.disabled(),
    errorBorderColor = colors.dangerText,
    focusedPlaceholderColor = colors.textSubtle,
    unfocusedPlaceholderColor = colors.textSubtle,
    errorPlaceholderColor = colors.textSubtle,
    focusedSuffixColor = colors.textMuted,
    unfocusedSuffixColor = colors.textMuted,
    errorSuffixColor = colors.textMuted,
  )
}

@Composable
fun LabelledField(
  label: String,
  modifier: Modifier = Modifier,
  hint: String? = null,
  error: String? = null,
  labelEnd: String? = null,
  field: @Composable () -> Unit,
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s6)) {
      Text(label, style = type.labelMedium, color = colors.textSubtle)
      if (labelEnd != null) Text(labelEnd, style = type.labelMedium, color = colors.textSubtle)
    }
    field()
    FieldNote(hint, error)
  }
}

@Composable
fun FieldNote(hint: String?, error: String?, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  when {
    error != null -> Row(
      modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
    ) {
      Icon(painterResource(Res.drawable.ic_error_20), null, Modifier.size(NoteIcon), tint = colors.dangerText)
      Text(error, style = type.bodySmall, color = colors.dangerText)
    }
    hint != null -> Text(hint, modifier, style = type.bodySmall, color = colors.textSubtle)
  }
}

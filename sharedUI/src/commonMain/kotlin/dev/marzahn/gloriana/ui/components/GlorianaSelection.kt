package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.disabled

@Composable
fun GlorianaCheckbox(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val colors = GlorianaTheme.colors
  ControlFrame(interactionSource, GlorianaTheme.shapes.tag, modifier) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = CheckboxDefaults.colors(
        checkedColor = colors.accent,
        uncheckedColor = colors.borderStrong,
        checkmarkColor = colors.accentFg,
        disabledCheckedColor = colors.accent.disabled(),
        disabledUncheckedColor = colors.borderStrong.disabled(),
      ),
      interactionSource = interactionSource,
    )
  }
}

@Composable
fun GlorianaRadioButton(
  selected: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val colors = GlorianaTheme.colors
  ControlFrame(interactionSource, GlorianaTheme.shapes.pill, modifier) {
    RadioButton(
      selected = selected,
      onClick = onClick,
      enabled = enabled,
      colors = RadioButtonDefaults.colors(
        selectedColor = colors.accent,
        unselectedColor = colors.borderStrong,
        disabledSelectedColor = colors.accent.disabled(),
        disabledUnselectedColor = colors.borderStrong.disabled(),
      ),
      interactionSource = interactionSource,
    )
  }
}

package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.InteractionState
import dev.marzahn.gloriana.ui.theme.LookColors
import dev.marzahn.gloriana.ui.theme.disabled
import dev.marzahn.gloriana.ui.theme.fillFor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val IconButtonSize = 40.dp

@Composable
fun GlorianaIconButton(
  icon: DrawableResource,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.control
  val container by animateLookColor(colors.fillFor(interactionSource.interactionState()))
  ControlFrame(interactionSource, shape, modifier) {
    IconButton(
      onClick = onClick,
      modifier = Modifier.size(IconButtonSize),
      enabled = enabled,
      shape = shape,
      colors = IconButtonDefaults.iconButtonColors(
        containerColor = container,
        contentColor = colors.textMuted,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = colors.textMuted.disabled(),
      ),
      interactionSource = interactionSource,
    ) {
      Icon(painterResource(icon), contentDescription)
    }
  }
}

private fun LookColors.uncheckedTrack(state: InteractionState): Color = when (state) {
  InteractionState.Rest -> surface2
  InteractionState.Hovered -> surface3
  InteractionState.Pressed -> border
}

private fun LookColors.checkedTrack(state: InteractionState): Color =
  if (state == InteractionState.Rest) accent else accentHover

@Composable
fun GlorianaSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val state = interactionSource.interactionState()
  val colors = GlorianaTheme.colors
  val checkedTrack by animateLookColor(colors.checkedTrack(state))
  val uncheckedTrack by animateLookColor(colors.uncheckedTrack(state))
  ControlFrame(interactionSource, GlorianaTheme.shapes.pill, modifier) {
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(
        checkedThumbColor = colors.accentFg,
        checkedTrackColor = checkedTrack,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = colors.accent,
        uncheckedThumbColor = colors.borderStrong,
        uncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = colors.borderStrong,
        uncheckedIconColor = colors.surface2,
        disabledCheckedThumbColor = colors.accentFg.disabled(),
        disabledCheckedTrackColor = colors.accent.disabled(),
        disabledCheckedBorderColor = Color.Transparent,
        disabledCheckedIconColor = colors.accent.disabled(),
        disabledUncheckedThumbColor = colors.borderStrong.disabled(),
        disabledUncheckedTrackColor = colors.surface2.disabled(),
        disabledUncheckedBorderColor = colors.borderStrong.disabled(),
        disabledUncheckedIconColor = colors.surface2.disabled(),
      ),
      interactionSource = interactionSource,
    )
  }
}

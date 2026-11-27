package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.InteractionState
import dev.marzahn.gloriana.ui.theme.LookShadow
import dev.marzahn.gloriana.ui.theme.disabled
import dev.marzahn.gloriana.ui.theme.fillFor
import dev.marzahn.gloriana.ui.theme.lookShadow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class ButtonKind { Primary, Secondary, Ghost, Danger }

enum class ButtonSize(val minHeight: Dp) { Regular(40.dp), Small(32.dp) }

private val ChipSize = 28.dp
private val IconSize = 18.dp

private class ButtonLook(
  val container: Color,
  val content: Color,
  val shape: Shape,
  val shadow: LookShadow?,
  val border: Color?,
)

@Composable
private fun buttonLook(kind: ButtonKind, state: InteractionState): ButtonLook {
  val colors = GlorianaTheme.colors
  val shapes = GlorianaTheme.shapes
  val shadows = GlorianaTheme.shadows
  return when (kind) {
    ButtonKind.Primary -> ButtonLook(
      container = if (state == InteractionState.Rest) colors.accent else colors.accentHover,
      content = colors.accentFg,
      shape = shapes.pill,
      shadow = shadows.warm,
      border = null,
    )
    ButtonKind.Secondary -> ButtonLook(
      container = if (state == InteractionState.Rest) colors.raised else colors.fillFor(state),
      content = colors.text,
      shape = shapes.control,
      shadow = shadows.soft,
      border = colors.borderStrong,
    )
    ButtonKind.Ghost ->
      ButtonLook(colors.fillFor(state), colors.textMuted, shapes.control, shadow = null, border = null)
    ButtonKind.Danger -> ButtonLook(
      container = if (state == InteractionState.Rest) colors.danger else colors.dangerText,
      content = colors.onDanger,
      shape = shapes.control,
      shadow = null,
      border = null,
    )
  }
}

@Composable
fun GlorianaButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  kind: ButtonKind = ButtonKind.Secondary,
  icon: DrawableResource? = null,
  size: ButtonSize = ButtonSize.Regular,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val look = buttonLook(kind, interactionSource.interactionState())
  val container by animateLookColor(look.container)
  val spacing = GlorianaTheme.spacing
  val shadow = if (enabled) look.shadow else null
  val border = look.border?.let { BorderStroke(1.dp, if (enabled) it else it.disabled()) }
  val padding = if (kind == ButtonKind.Primary && icon != null) {
    PaddingValues(start = spacing.s20, end = spacing.s6)
  } else {
    PaddingValues(horizontal = spacing.s14)
  }
  ControlFrame(interactionSource, look.shape, modifier) {
    Button(
      onClick = onClick,
      modifier = Modifier
        .then(if (shadow != null) Modifier.lookShadow(shadow, look.shape) else Modifier)
        .heightIn(min = size.minHeight),
      enabled = enabled,
      shape = look.shape,
      colors = ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = look.content,
        disabledContainerColor = look.container.disabled(),
        disabledContentColor = look.content.disabled(),
      ),
      elevation = null,
      border = border,
      contentPadding = padding,
      interactionSource = interactionSource,
    ) {
      Text(label, style = GlorianaTheme.type.material.labelLarge)
      if (icon != null) {
        Spacer(Modifier.width(spacing.s8))
        ButtonIcon(icon, chip = kind == ButtonKind.Primary, enabled = enabled)
      }
    }
  }
}

@Composable
private fun ButtonIcon(icon: DrawableResource, chip: Boolean, enabled: Boolean) {
  val chipColor = GlorianaTheme.colors.accentChip.let { if (enabled) it else it.disabled() }
  val chipModifier = if (chip) Modifier.size(ChipSize).background(chipColor, GlorianaTheme.shapes.pill) else Modifier
  Box(chipModifier, contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(IconSize))
  }
}

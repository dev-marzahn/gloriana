package dev.marzahn.gloriana.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.InteractionState
import dev.marzahn.gloriana.ui.theme.focusRing

@Composable
internal fun InteractionSource.interactionState(): InteractionState {
  val hovered by collectIsHoveredAsState()
  val pressed by collectIsPressedAsState()
  return when {
    pressed -> InteractionState.Pressed
    hovered -> InteractionState.Hovered
    else -> InteractionState.Rest
  }
}

@Composable
internal fun animateLookColor(target: Color): State<Color> =
  animateColorAsState(target, GlorianaTheme.motion.standard.tween(), label = "fill")

@Composable
internal fun ControlFrame(
  interactionSource: MutableInteractionSource,
  shape: Shape,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
    Box(Modifier.focusRing(interactionSource, shape), propagateMinConstraints = true) {
      CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
    }
  }
}

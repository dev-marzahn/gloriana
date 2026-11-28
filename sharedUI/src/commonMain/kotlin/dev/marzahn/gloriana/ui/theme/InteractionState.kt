package dev.marzahn.gloriana.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

enum class InteractionState { Rest, Hovered, Pressed }

fun <T> InteractionState.pick(rest: T, active: T): T = if (this == InteractionState.Rest) rest else active

fun LookColors.fillFor(state: InteractionState): Color = when (state) {
  InteractionState.Rest -> Color.Transparent
  InteractionState.Hovered -> surface2
  InteractionState.Pressed -> surface3
}

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

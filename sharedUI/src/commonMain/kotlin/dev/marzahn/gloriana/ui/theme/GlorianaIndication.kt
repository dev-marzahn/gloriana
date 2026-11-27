package dev.marzahn.gloriana.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class InteractionState { Rest, Hovered, Pressed }

fun LookColors.fillFor(state: InteractionState): Color = when (state) {
  InteractionState.Rest -> Color.Transparent
  InteractionState.Hovered -> surface2
  InteractionState.Pressed -> surface3
}

data class GlorianaIndication(val shape: Shape) : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode =
    IndicationNode(interactionSource, shape, fills = true)
}

data class GlorianaFocusRing(val shape: Shape) : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode =
    IndicationNode(interactionSource, shape, fills = false)
}

fun Modifier.focusRing(interactionSource: InteractionSource, shape: Shape): Modifier =
  indication(interactionSource, GlorianaFocusRing(shape))

private val RingGap = 2.dp
private val RingWidth = 2.dp

private class IndicationNode(
  private val interactionSource: InteractionSource,
  private val shape: Shape,
  private val fills: Boolean,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
  private val hovers = mutableSetOf<HoverInteraction.Enter>()
  private val presses = mutableSetOf<PressInteraction.Press>()
  private val focuses = mutableSetOf<FocusInteraction.Focus>()
  private val hovered = Animatable(0f)
  private val pressed = Animatable(0f)

  private val look: Look get() = currentValueOf(LocalLook)

  override fun onAttach() {
    coroutineScope.launch {
      interactionSource.interactions.collect { interaction ->
        record(interaction)
        invalidateDraw()
        if (fills) {
          val spec = look.tokens.motion.standard.tween<Float>()
          launch { hovered.animateTo(if (hovers.isEmpty()) 0f else 1f, spec) }
          launch { pressed.animateTo(if (presses.isEmpty()) 0f else 1f, spec) }
        }
      }
    }
  }

  private fun record(interaction: Interaction) {
    when (interaction) {
      is HoverInteraction.Enter -> hovers += interaction
      is HoverInteraction.Exit -> hovers -= interaction.enter
      is PressInteraction.Press -> presses += interaction
      is PressInteraction.Release -> presses -= interaction.press
      is PressInteraction.Cancel -> presses -= interaction.press
      is FocusInteraction.Focus -> focuses += interaction
      is FocusInteraction.Unfocus -> focuses -= interaction.focus
    }
  }

  override fun ContentDrawScope.draw() {
    val colors = look.set.colors
    if (fills) {
      val outline = shape.createOutline(size, layoutDirection, this)
      drawOutline(outline, colors.fillFor(InteractionState.Hovered), alpha = hovered.value)
      drawOutline(outline, colors.fillFor(InteractionState.Pressed), alpha = pressed.value)
    }
    drawContent()
    if (focuses.isNotEmpty()) {
      val inset = (RingGap + RingWidth / 2).toPx()
      val ringSize = Size(size.width + inset * 2, size.height + inset * 2)
      translate(-inset, -inset) {
        val ring = shape.createOutline(ringSize, layoutDirection, this)
        drawOutline(ring, colors.accent, style = Stroke(RingWidth.toPx()))
      }
    }
  }
}

package dev.marzahn.gloriana.ui.characters.fields

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.ButtonSize
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_expand_less_20
import dev.marzahn.gloriana.ui.resources.ic_expand_more_20
import dev.marzahn.gloriana.ui.resources.show_all
import dev.marzahn.gloriana.ui.resources.show_less
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

private const val FADING_LINES = 2

@Composable
fun Folding(
  lines: Int,
  panel: Color,
  modifier: Modifier = Modifier,
  toggle: Boolean = true,
  content: @Composable () -> Unit,
) {
  val lineHeightPx = with(LocalDensity.current) { GlorianaTheme.type.reading.lineHeight.toPx() }
  val limit = (lines * lineHeightPx).toInt()
  val fade = lineHeightPx * FADING_LINES
  var unfolded by remember { mutableStateOf(false) }
  var overflows by remember { mutableStateOf(false) }
  val folded = overflows && !unfolded
  Column(modifier) {
    Layout(
      content = content,
      modifier = Modifier
        .animateContentSize(GlorianaTheme.motion.standard.tween())
        .clipToBounds()
        .drawWithContent {
          drawContent()
          if (folded) {
            val fadeOut = listOf(panel.copy(alpha = 0f), panel)
            drawRect(Brush.verticalGradient(fadeOut, startY = size.height - fade, endY = size.height))
          }
        },
    ) { measurables, constraints ->
      val placeables = measurables.map { it.measure(constraints.copy(maxHeight = Constraints.Infinity)) }
      val fullHeight = placeables.maxOfOrNull { it.height } ?: 0
      overflows = fullHeight > limit
      val height = if (overflows && !unfolded) limit else fullHeight
      layout(constraints.maxWidth, height) { placeables.forEach { it.place(0, 0) } }
    }
    if (overflows && toggle) {
      val label = if (unfolded) Res.string.show_less else Res.string.show_all
      val icon = if (unfolded) Res.drawable.ic_expand_less_20 else Res.drawable.ic_expand_more_20
      val toggleFold = { unfolded = !unfolded }
      GlorianaButton(stringResource(label), toggleFold, kind = ButtonKind.Ghost, icon = icon, size = ButtonSize.Small)
    }
  }
}

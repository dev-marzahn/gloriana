package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.theme.GlorianaTheme

private val DotSlot = 14.dp
private val Dot = 8.dp

@Composable
fun RunningLine(text: String, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val pill = GlorianaTheme.shapes.pill
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
  ) {
    Box(Modifier.size(DotSlot).background(colors.accentRing, pill), Alignment.Center) {
      Box(Modifier.size(Dot).background(colors.accent, pill))
    }
    Text(text, style = GlorianaTheme.type.material.bodySmall, color = colors.accentText)
  }
}

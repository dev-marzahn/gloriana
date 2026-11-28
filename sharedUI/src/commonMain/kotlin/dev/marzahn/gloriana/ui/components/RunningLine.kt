package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.theme.GlorianaTheme

@Composable
fun RunningLine(text: String, modifier: Modifier = Modifier) {
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
  ) {
    RunningDot()
    Text(text, style = GlorianaTheme.type.material.bodySmall, color = GlorianaTheme.colors.accentText)
  }
}

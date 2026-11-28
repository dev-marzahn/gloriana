package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_arrow_back
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.lookShadow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

typealias PanelRow = @Composable () -> Unit

private val PanelIconSize = 20.dp
private val BackIconSize = 16.dp
private val BackButtonHeight = 40.dp

@Composable
fun Panel(icon: DrawableResource, title: String, rows: List<PanelRow>, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.panel
  val spacing = GlorianaTheme.spacing
  Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s8)) {
      Icon(painterResource(icon), null, Modifier.size(PanelIconSize), tint = colors.textSubtle)
      Text(title, style = GlorianaTheme.type.material.titleLarge, color = colors.text)
    }
    Column(
      Modifier
        .fillMaxWidth()
        .lookShadow(GlorianaTheme.shadows.soft, shape)
        .background(colors.raised, shape)
        .border(1.dp, colors.border, shape),
    ) {
      rows.forEachIndexed { index, row ->
        if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
        Box(Modifier.fillMaxWidth().padding(horizontal = spacing.s16, vertical = spacing.s14)) { row() }
      }
    }
  }
}

@Composable
fun BackButton(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Row(
    modifier
      .heightIn(min = BackButtonHeight)
      .tappable(GlorianaTheme.shapes.control, onClick = onBack)
      .padding(horizontal = spacing.s8),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s6),
  ) {
    Icon(painterResource(Res.drawable.ic_arrow_back), null, Modifier.size(BackIconSize), tint = colors.textMuted)
    Text(label, style = GlorianaTheme.type.material.labelLarge, color = colors.textMuted)
  }
}

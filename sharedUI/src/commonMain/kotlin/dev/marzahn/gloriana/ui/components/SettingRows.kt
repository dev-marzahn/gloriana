package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_info_20
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource

private val InfoIconSize = 16.dp

@Composable
fun SettingRow(
  title: String,
  hint: AnnotatedString?,
  error: String?,
  modifier: Modifier = Modifier,
  control: @Composable () -> Unit,
) {
  Row(
    modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s12),
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = GlorianaTheme.type.material.bodyLarge, color = GlorianaTheme.colors.text)
      FieldNote(hint, error)
    }
    control()
  }
}

@Composable
fun InfoLine(text: String, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
  ) {
    Icon(painterResource(Res.drawable.ic_info_20), null, Modifier.size(InfoIconSize), tint = colors.textSubtle)
    Text(text, style = GlorianaTheme.type.material.bodySmall, color = colors.textSubtle)
  }
}

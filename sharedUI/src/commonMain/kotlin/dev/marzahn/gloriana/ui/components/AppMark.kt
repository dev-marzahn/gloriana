package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import dev.marzahn.gloriana.ui.resources.ic_app_mark
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MarkSize = 36.dp
private val CrownSize = 22.dp

@Composable
fun AppMark(modifier: Modifier = Modifier) {
  val name = stringResource(Res.string.app_name)
  Box(
    modifier
      .size(MarkSize)
      .background(GlorianaTheme.colors.accent, GlorianaTheme.shapes.control)
      .semantics { contentDescription = name },
    contentAlignment = Alignment.Center,
  ) {
    val crown = painterResource(Res.drawable.ic_app_mark)
    Icon(crown, contentDescription = null, modifier = Modifier.size(CrownSize), tint = GlorianaTheme.colors.accentFg)
  }
}

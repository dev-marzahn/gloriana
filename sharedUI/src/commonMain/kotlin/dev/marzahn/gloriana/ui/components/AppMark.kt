package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.brand.AppMarkCrown
import dev.marzahn.gloriana.logic.brand.OutlinePoint
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

private val MarkSize = 36.dp
private val CrownSize = 22.dp

private val AppMarkCrownVector: ImageVector = ImageVector.Builder(
  name = "AppMarkCrown",
  defaultWidth = AppMarkCrown.VIEWPORT.dp,
  defaultHeight = AppMarkCrown.VIEWPORT.dp,
  viewportWidth = AppMarkCrown.VIEWPORT,
  viewportHeight = AppMarkCrown.VIEWPORT,
).path(fill = SolidColor(Color.Black)) {
  AppMarkCrown.shapes.forEach { outline(it) }
}.build()

private fun PathBuilder.outline(points: List<OutlinePoint>) {
  moveTo(points.first().x, points.first().y)
  points.drop(1).forEach { lineTo(it.x, it.y) }
  close()
}

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
    Icon(
      AppMarkCrownVector,
      contentDescription = null,
      modifier = Modifier.size(CrownSize),
      tint = GlorianaTheme.colors.accentFg,
    )
  }
}

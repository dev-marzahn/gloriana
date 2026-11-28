package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_add
import dev.marzahn.gloriana.ui.theme.GlorianaIndication
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Spacing
import dev.marzahn.gloriana.ui.theme.animateLookColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class ListDensity { Screen, Pane }

enum class RowSelection { NotSelectable, Unselected, Selected }

sealed interface RowLine {
  data class Plain(val text: String) : RowLine

  data class Running(val text: String) : RowLine

  data class Flagged(val icon: DrawableResource, val text: String, val tone: LineTone) : RowLine
}

enum class LineTone { Muted, Danger }

private val FlagIconSize = 16.dp

private val ListDensity.rowHeight: Dp
  get() = when (this) {
    ListDensity.Screen -> 72.dp
    ListDensity.Pane -> 56.dp
  }

private val ListDensity.addRowHeight: Dp
  get() = when (this) {
    ListDensity.Screen -> 48.dp
    ListDensity.Pane -> 40.dp
  }

private fun ListDensity.rowSidePadding(spacing: Spacing): Dp = when (this) {
  ListDensity.Screen -> spacing.s8
  ListDensity.Pane -> spacing.s10
}

private val DotSlot = 14.dp
private val Dot = 8.dp
private val DotHalo = 3.dp

private fun Modifier.rowClick(selection: RowSelection, shape: Shape, onClick: () -> Unit): Modifier {
  val indication = GlorianaIndication(shape)
  return if (selection == RowSelection.NotSelectable) {
    clickable(interactionSource = null, indication = indication, role = Role.Button, onClick = onClick)
  } else {
    selectable(
      selected = selection == RowSelection.Selected,
      interactionSource = null,
      indication = indication,
      onClick = onClick,
    )
  }
}

@Composable
fun ListRow(
  name: String,
  line: RowLine,
  onClick: () -> Unit,
  avatar: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  selection: RowSelection = RowSelection.NotSelectable,
  density: ListDensity = ListDensity.Screen,
  time: String? = null,
  nameMark: (@Composable () -> Unit)? = null,
  thirdLine: String? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.control
  val type = GlorianaTheme.type.material
  val selected = selection == RowSelection.Selected
  val fill by animateLookColor(if (selected) colors.accentSoft else Color.Transparent)
  val ring by animateLookColor(if (selected) colors.accentRing else Color.Transparent)
  Row(
    modifier
      .fillMaxWidth()
      .rowClick(selection, shape, onClick)
      .background(fill, shape)
      .border(1.dp, ring, shape)
      .heightIn(min = density.rowHeight)
      .padding(horizontal = density.rowSidePadding(spacing), vertical = spacing.s10),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s12),
  ) {
    avatar()
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s6)) {
        Text(
          name,
          Modifier.weight(1f, fill = false),
          style = type.titleMedium,
          color = colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        nameMark?.invoke()
      }
      RowLineText(line)
      if (thirdLine != null) {
        Text(
          thirdLine,
          style = type.bodySmall,
          color = colors.textSubtle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (time != null) Text(time, style = type.labelSmall, color = colors.textSubtle)
    trailing?.invoke()
  }
}

private val MarkHeight = 22.dp

@Composable
fun Mark(text: String, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.tag
  Box(
    modifier
      .heightIn(min = MarkHeight)
      .background(colors.surface2, shape)
      .border(1.dp, colors.borderSubtle, shape)
      .padding(horizontal = GlorianaTheme.spacing.s6),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = GlorianaTheme.type.material.labelMedium, color = colors.textMuted, maxLines = 1)
  }
}

private val ListDensity.tileSize: Dp
  get() = when (this) {
    ListDensity.Screen -> 40.dp
    ListDensity.Pane -> 36.dp
  }

private val ListDensity.tileIconSize: Dp
  get() = when (this) {
    ListDensity.Screen -> 20.dp
    ListDensity.Pane -> 18.dp
  }

@Composable
fun RowTile(icon: DrawableResource, density: ListDensity, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  Box(
    modifier.size(density.tileSize).background(colors.surface2, GlorianaTheme.shapes.control),
    contentAlignment = Alignment.Center,
  ) {
    Icon(painterResource(icon), null, Modifier.size(density.tileIconSize), tint = colors.textMuted)
  }
}

@Composable
fun RunningDot(modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  Box(modifier.size(DotSlot), contentAlignment = Alignment.Center) {
    Box(Modifier.size(Dot + DotHalo * 2).background(colors.accentRing, GlorianaTheme.shapes.pill))
    Box(Modifier.size(Dot).background(colors.accent, GlorianaTheme.shapes.pill))
  }
}

@Composable
private fun RowLineText(line: RowLine) {
  val colors = GlorianaTheme.colors
  val style = GlorianaTheme.type.material.bodyMedium
  when (line) {
    is RowLine.Plain ->
      Text(line.text, style = style, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    is RowLine.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
      RunningDot()
      Spacer(Modifier.width(GlorianaTheme.spacing.s6))
      Text(line.text, style = style, color = colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    is RowLine.Flagged -> Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
    ) {
      val tint = if (line.tone == LineTone.Danger) colors.dangerText else colors.textMuted
      Icon(painterResource(line.icon), null, Modifier.size(FlagIconSize), tint = tint)
      Text(line.text, style = style, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

private val DashOn = 4.dp
private val DashOff = 3.dp

@Composable
fun AddRow(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  density: ListDensity = ListDensity.Screen,
) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.control
  val radius = GlorianaTheme.shapes.controlRadius
  Row(
    modifier
      .fillMaxWidth()
      .rowClick(RowSelection.NotSelectable, shape, onClick)
      .drawBehind {
        val stroke = 1.dp.toPx()
        drawRoundRect(
          color = colors.borderStrong,
          topLeft = Offset(stroke / 2, stroke / 2),
          size = Size(size.width - stroke, size.height - stroke),
          cornerRadius = CornerRadius(radius.toPx()),
          style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
        )
      }
      .heightIn(min = density.addRowHeight)
      .padding(spacing.s12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    Icon(painterResource(Res.drawable.ic_add), null, tint = colors.textMuted)
    Text(label, style = GlorianaTheme.type.material.labelLarge, color = colors.textMuted)
  }
}

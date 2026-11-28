package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.theme.GlorianaIndication
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.animateLookColor
import dev.marzahn.gloriana.ui.theme.disabledUnless
import dev.marzahn.gloriana.ui.theme.lookShadow

private val TrackInset = 3.dp
private val SegmentMinHeight = 30.dp

class Segment<T>(val value: T, val label: String)

enum class SegmentWidth { Scrolling, Filling }

@Composable
fun <T> SegmentedControl(
  segments: List<Segment<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  width: SegmentWidth = SegmentWidth.Scrolling,
  enabled: Boolean = true,
) {
  val colors = GlorianaTheme.colors
  val shapes = GlorianaTheme.shapes
  val track = Modifier.background(colors.surface2, shapes.control).padding(TrackInset).selectableGroup()
  val row = @Composable { rowModifier: Modifier ->
    Row(rowModifier.then(track), verticalAlignment = Alignment.CenterVertically) {
      segments.forEach { segment ->
        SegmentView(segment, segment.value == selected, enabled, width, { onSelect(segment.value) })
      }
    }
  }
  when (width) {
    SegmentWidth.Filling -> row(modifier.fillMaxWidth())
    SegmentWidth.Scrolling -> Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { row(Modifier) }
  }
}

@Composable
private fun RowScope.SegmentView(
  segment: Segment<*>,
  chosen: Boolean,
  enabled: Boolean,
  width: SegmentWidth,
  onClick: () -> Unit,
) {
  val colors = GlorianaTheme.colors
  val shape = RoundedCornerShape(GlorianaTheme.shapes.controlRadius - TrackInset)
  val fill by animateLookColor(if (chosen) colors.raised else Color.Transparent)
  val bringIntoView = remember { BringIntoViewRequester() }
  LaunchedEffect(chosen) { if (chosen) bringIntoView.bringIntoView() }
  val sized = if (width == SegmentWidth.Filling) Modifier.weight(1f) else Modifier
  Box(
    sized
      .bringIntoViewRequester(bringIntoView)
      .selectable(
        chosen,
        enabled = enabled,
        role = Role.Tab,
        interactionSource = null,
        indication = GlorianaIndication(shape),
        onClick = onClick,
      )
      .minimumInteractiveComponentSize()
      .heightIn(min = SegmentMinHeight)
      .then(if (chosen) Modifier.lookShadow(GlorianaTheme.shadows.soft, shape) else Modifier)
      .background(fill, shape)
      .padding(horizontal = GlorianaTheme.spacing.s12),
    contentAlignment = Alignment.Center,
  ) {
    val textColor = if (chosen) colors.text else colors.textMuted
    Text(
      segment.label,
      style = GlorianaTheme.type.material.labelLarge,
      color = textColor.disabledUnless(enabled),
      maxLines = 1,
    )
  }
}

package dev.marzahn.gloriana.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.components.AppMark
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.LookColors
import dev.marzahn.gloriana.ui.theme.focusRing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val RailWidth = 80.dp
private val RailEdge = 12.dp
private val MarkToFirstItem = 14.dp
private val RailItemGap = 6.dp

private val RailInsets = WindowInsetsSides.Start + WindowInsetsSides.Vertical

@Composable
private fun LookColors.barItemColors(): NavigationBarItemColors = NavigationBarItemDefaults.colors(
  selectedIconColor = accentText,
  selectedTextColor = text,
  indicatorColor = accentSoft,
  unselectedIconColor = textMuted,
  unselectedTextColor = textMuted,
)

@Composable
private fun LookColors.railItemColors(): NavigationRailItemColors = NavigationRailItemDefaults.colors(
  selectedIconColor = accentText,
  selectedTextColor = text,
  indicatorColor = accentSoft,
  unselectedIconColor = textMuted,
  unselectedTextColor = textMuted,
)

@Composable
private fun DestinationIcon(destination: Destination, selected: Boolean, contentDescription: String?) {
  Icon(painterResource(if (selected) destination.selectedIcon else destination.icon), contentDescription)
}

@Composable
private fun DestinationLabel(destination: Destination) {
  Text(stringResource(destination.label), style = GlorianaTheme.type.material.labelMedium, maxLines = 1)
}

@Composable
internal fun GlorianaNavigationBar(
  current: Destination,
  onSelect: (Destination) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val itemColors = colors.barItemColors()
  val ringShape = GlorianaTheme.shapes.control
  Column(modifier) {
    HorizontalDivider(color = colors.border)
    NavigationBar(containerColor = colors.rail, tonalElevation = 0.dp) {
      Destination.entries.forEach { destination ->
        val selected = destination == current
        val source = remember { MutableInteractionSource() }
        NavigationBarItem(
          selected = selected,
          onClick = { onSelect(destination) },
          icon = { DestinationIcon(destination, selected, contentDescription = null) },
          modifier = Modifier.focusRing(source, ringShape),
          label = { DestinationLabel(destination) },
          colors = itemColors,
          interactionSource = source,
        )
      }
    }
  }
}

@Composable
internal fun LabelledNavigationRail(
  current: Destination,
  onSelect: (Destination) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val itemColors = colors.railItemColors()
  val ringShape = GlorianaTheme.shapes.control
  Row(modifier.fillMaxHeight()) {
    NavigationRail(containerColor = colors.rail, windowInsets = WindowInsets.safeDrawing.only(RailInsets)) {
      Destination.entries.forEach { destination ->
        val selected = destination == current
        val source = remember { MutableInteractionSource() }
        NavigationRailItem(
          selected = selected,
          onClick = { onSelect(destination) },
          icon = { DestinationIcon(destination, selected, contentDescription = null) },
          modifier = Modifier.focusRing(source, ringShape),
          label = { DestinationLabel(destination) },
          colors = itemColors,
          interactionSource = source,
        )
      }
    }
    VerticalDivider(color = colors.border)
  }
}

@Composable
internal fun WorkbenchRail(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val itemColors = colors.railItemColors()
  val item = @Composable { destination: Destination ->
    IconOnlyRailItem(destination, destination == current, onSelect, itemColors)
  }
  Row(modifier.fillMaxHeight().background(colors.rail)) {
    Column(
      Modifier
        .fillMaxHeight()
        .width(RailWidth)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(RailInsets))
        .padding(vertical = RailEdge)
        .selectableGroup(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AppMark()
      Spacer(Modifier.height(MarkToFirstItem))
      Column(verticalArrangement = Arrangement.spacedBy(RailItemGap)) {
        Destination.entries.filter { it.railPlacement == RailPlacement.Top }.forEach { item(it) }
      }
      Spacer(Modifier.weight(1f))
      Column(verticalArrangement = Arrangement.spacedBy(RailItemGap)) {
        Destination.entries.filter { it.railPlacement == RailPlacement.Foot }.forEach { item(it) }
      }
    }
    VerticalDivider(color = colors.border)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconOnlyRailItem(
  destination: Destination,
  selected: Boolean,
  onSelect: (Destination) -> Unit,
  itemColors: NavigationRailItemColors,
  modifier: Modifier = Modifier,
) {
  val label = stringResource(destination.label)
  val source = remember { MutableInteractionSource() }
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.End),
    tooltip = {
      PlainTooltip(containerColor = GlorianaTheme.colors.inverse, contentColor = GlorianaTheme.colors.inverseFg) {
        Text(label)
      }
    },
    state = rememberTooltipState(),
    modifier = modifier,
  ) {
    NavigationRailItem(
      selected = selected,
      onClick = { onSelect(destination) },
      icon = { DestinationIcon(destination, selected, contentDescription = label) },
      modifier = Modifier.focusRing(source, GlorianaTheme.shapes.control),
      colors = itemColors,
      interactionSource = source,
    )
  }
}

package dev.marzahn.gloriana.ui.presets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.ui.components.AddRow
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.add_group
import dev.marzahn.gloriana.ui.resources.entries_panel
import dev.marzahn.gloriana.ui.resources.entries_panel_hint
import dev.marzahn.gloriana.ui.resources.ic_drag_indicator
import dev.marzahn.gloriana.ui.resources.ic_format_list_numbered
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.move_up
import dev.marzahn.gloriana.ui.resources.moved_entry
import dev.marzahn.gloriana.ui.resources.moved_entry_no_group
import dev.marzahn.gloriana.ui.resources.moved_group
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.lookShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private val PanelIconSize = 20.dp
private val HintIconSize = 16.dp

class EntriesActions(
  val onIntent: (PresetIntent) -> Unit,
  val onOpenEntry: (Uuid) -> Unit,
  val onAskGroup: (GroupPrompt) -> Unit,
)

private class RowDrag(private val scope: CoroutineScope) {
  var dragging by mutableStateOf<Uuid?>(null)
    private set
  var settling by mutableStateOf<Uuid?>(null)
    private set
  private var total by mutableFloatStateOf(0f)
  private var startTop = 0f
  private var movedFrom: List<PresetRow>? = null
  private val drop = Animatable(0f)
  val heights = mutableStateMapOf<Uuid, Int>()

  fun start(id: Uuid, layout: PanelLayout) {
    dragging = id
    total = 0f
    startTop = layout.tops[id] ?: 0f
    movedFrom = null
  }

  fun offset(id: Uuid, layout: PanelLayout): Float = when (id) {
    dragging -> startTop + total - (layout.tops[id] ?: startTop)
    settling -> drop.value
    else -> 0f
  }

  fun step(id: Uuid, dy: Float, layout: PanelLayout, move: (MoveDirection) -> Unit) {
    total += dy
    if (movedFrom == layout.rows) return
    layout.step(id, startTop + total)?.let {
      movedFrom = layout.rows
      move(it)
    }
  }

  fun keepHeightsOf(rows: List<PresetRow>) {
    val ids = rows.map { it.id }.toSet()
    heights.keys.retainAll(ids)
  }

  fun end(id: Uuid, layout: PanelLayout, settle: AnimationSpec<Float>) {
    val from = offset(id, layout)
    dragging = null
    settling = id
    scope.launch {
      drop.snapTo(from)
      drop.animateTo(0f, settle)
      if (settling == id) settling = null
    }
  }
}

private class PanelState(
  val layout: () -> PanelLayout,
  val drag: RowDrag,
  val hiddenGroups: Set<Uuid>,
  val moveLabels: Map<MoveDirection, String>,
  val onMove: (Uuid, MoveDirection) -> Unit,
  val onFold: (Uuid) -> Unit,
)

@Composable
fun EntriesPanel(rows: List<PresetRow>, actions: EntriesActions, modifier: Modifier = Modifier) {
  var folded by rememberSaveable { mutableStateOf(emptyList<String>()) }
  var announced by remember { mutableStateOf<Uuid?>(null) }
  val scope = rememberCoroutineScope()
  val drag = remember(scope) { RowDrag(scope) }
  LaunchedEffect(drag, rows) { drag.keepHeightsOf(rows) }
  val draggedGroup = drag.dragging?.takeIf { id -> rows.any { it is PresetGroup && it.id == id } }
  val hiddenGroups = folded.map(Uuid::parse).toSet() + listOfNotNull(draggedGroup)
  val layout = PanelLayout(rows, hiddenRows(rows, hiddenGroups), drag.heights)
  val currentLayout by rememberUpdatedState(layout)
  val state = PanelState(
    layout = { currentLayout },
    drag = drag,
    hiddenGroups = hiddenGroups,
    moveLabels = mapOf(
      MoveDirection.Up to stringResource(Res.string.move_up),
      MoveDirection.Down to stringResource(Res.string.move_down),
    ),
    onMove = { id, direction ->
      folded = folded - unfoldedBy(currentLayout.rows, id, direction)
      actions.onIntent(PresetIntent.Move(id, direction))
      announced = id
    },
    onFold = { id ->
      val key = id.toString()
      folded = if (key in folded) folded - key else folded + key
    },
  )
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    PanelTitle()
    PanelBody(rows, state, actions)
    Announcement(rows, announced)
  }
}

private fun hiddenRows(rows: List<PresetRow>, hiddenGroups: Set<Uuid>): Set<Uuid> =
  hiddenGroups.flatMap { group -> rows.entriesUnder(group).map { it.id } }.toSet()

@Composable
private fun PanelTitle() {
  val colors = GlorianaTheme.colors
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8),
  ) {
    Icon(
      painterResource(Res.drawable.ic_format_list_numbered),
      null,
      Modifier.size(PanelIconSize),
      tint = colors.textSubtle,
    )
    Text(stringResource(Res.string.entries_panel), style = GlorianaTheme.type.material.titleLarge, color = colors.text)
  }
}

@Composable
private fun PanelBody(rows: List<PresetRow>, state: PanelState, actions: EntriesActions) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.panel
  LookaheadScope {
    Column(
      Modifier
        .fillMaxWidth()
        .lookShadow(GlorianaTheme.shadows.soft, shape)
        .background(colors.raised, shape)
        .border(1.dp, colors.border, shape),
    ) {
      PanelHint()
      var group: PresetGroup? = null
      rows.forEach { row ->
        if (row is PresetGroup) group = row
        val owner = group
        key(row.id) {
          AnimatedRow(hidden = row.id in state.layout().hidden) { PanelRow(row, owner, state, actions) }
        }
      }
      HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
      AddRow(
        stringResource(Res.string.add_group),
        { actions.onAskGroup(GroupPrompt.Add) },
        Modifier.padding(GlorianaTheme.spacing.s8),
        ListDensity.Pane,
      )
    }
  }
}

@Composable
private fun AnimatedRow(hidden: Boolean, content: @Composable () -> Unit) {
  val motion = GlorianaTheme.motion
  AnimatedVisibility(
    !hidden,
    enter = fadeIn(motion.enter.tween()) + expandVertically(motion.enter.tween()),
    exit = fadeOut(motion.exit.tween()) + shrinkVertically(motion.exit.tween()),
  ) { content() }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LookaheadScope.PanelRow(row: PresetRow, owner: PresetGroup?, state: PanelState, actions: EntriesActions) {
  val drag = state.drag
  val motion = GlorianaTheme.motion
  val lifted = drag.dragging == row.id
  val lift by animateFloatAsState(if (lifted) 1f else 0f, motion.standard.tween())
  val elevated = GlorianaTheme.shadows.elevated
  val placement = if (lifted || drag.settling == row.id) {
    Modifier
      .zIndex(1f)
      .graphicsLayer { translationY = drag.offset(row.id, state.layout()) }
  } else {
    Modifier.animateBounds(
      this,
      boundsTransform = { _, _ -> tween(motion.emphasized.durationMillis, easing = motion.emphasized.easing) },
    )
  }
  val handleModifier = Modifier.pointerInput(row.id) {
    detectDragGestures(
      onDragStart = { drag.start(row.id, state.layout()) },
      onDrag = { change, amount ->
        change.consume()
        drag.step(row.id, amount.y, state.layout()) { state.onMove(row.id, it) }
      },
      onDragEnd = { drag.end(row.id, state.layout(), motion.standard.tween()) },
      onDragCancel = { drag.end(row.id, state.layout(), motion.standard.tween()) },
    )
  }
  val movesModifier = Modifier.semantics {
    customActions = MoveDirection.entries.filter { state.layout().rows.canMove(row.id, it) }.map { direction ->
      CustomAccessibilityAction(state.moveLabels.getValue(direction)) {
        state.onMove(row.id, direction)
        true
      }
    }
  }
  Column(
    Modifier
      .onSizeChanged { drag.heights[row.id] = it.height }
      .then(placement)
      .lookShadow(elevated.copy(elevation = elevated.elevation * lift), GlorianaTheme.shapes.control)
      .background(GlorianaTheme.colors.raised.copy(alpha = lift), GlorianaTheme.shapes.control),
  ) {
    HorizontalDivider(thickness = 1.dp, color = GlorianaTheme.colors.borderSubtle)
    when (row) {
      is PresetEntry -> EntryRow(row, indented = owner != null, actions, handleModifier, movesModifier)
      is PresetGroup -> GroupRow(
        row,
        state.layout().rows.entriesUnder(row.id),
        folded = row.id in state.hiddenGroups,
        onFold = { state.onFold(row.id) },
        actions = actions,
        handleModifier = handleModifier,
        movesModifier = movesModifier,
      )
    }
  }
}

@Composable
private fun PanelHint() {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Row(
    Modifier.padding(horizontal = spacing.s16, vertical = spacing.s12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s6),
  ) {
    Icon(painterResource(Res.drawable.ic_drag_indicator), null, Modifier.size(HintIconSize), tint = colors.textSubtle)
    Text(
      stringResource(Res.string.entries_panel_hint),
      style = GlorianaTheme.type.material.bodySmall,
      color = colors.textSubtle,
    )
  }
}

@Composable
private fun announcement(row: PresetRow, place: RowPlace): String = when (row) {
  is PresetGroup -> stringResource(Res.string.moved_group, row.name, place.position, place.of)
  is PresetEntry -> place.group?.let { group ->
    stringResource(Res.string.moved_entry, row.name, place.position, place.of, group.name)
  } ?: stringResource(Res.string.moved_entry_no_group, row.name, place.position, place.of)
}

@Composable
private fun Announcement(rows: List<PresetRow>, moved: Uuid?) {
  val row = rows.firstOrNull { it.id == moved }
  val place = row?.let { rows.placeOf(it.id) }
  val text = if (row == null || place == null) null else announcement(row, place)
  if (text != null) {
    Box(
      Modifier.size(0.dp).semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = text
      },
    )
  }
}

private fun unfoldedBy(rows: List<PresetRow>, id: Uuid, direction: MoveDirection): Set<String> {
  val movesEntry = rows.firstOrNull { it.id == id } is PresetEntry
  val entering = rows.moved(id, direction).groupOf(id)
  return setOfNotNull(entering?.id?.toString()?.takeIf { movesEntry })
}

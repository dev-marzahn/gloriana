package dev.marzahn.gloriana.ui.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.marzahn.gloriana.ui.components.listMotion
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_drag_indicator
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.move_up
import dev.marzahn.gloriana.ui.resources.reorder_move_row
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.focusRing
import dev.marzahn.gloriana.ui.theme.lookShadow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private val HandleWidth = 32.dp
private val HandleHeight = 48.dp
private val HandleIcon = 20.dp
private val ScrollEdge = 64.dp
private val FastestScroll = 24.dp

internal class RowOrder(
  private val list: ReorderableList,
  val arranged: List<Uuid>,
  private val names: Map<Uuid, String>,
  private val settle: AnimationSpec<Float>,
) {
  val lift: RowLift get() = list.lift

  val active: Boolean get() = list.active

  val notMoved: NotMoved? get() = list.notMoved

  val shown: List<Uuid> get() = lift.shown(arranged)

  fun keyOf(id: Uuid): Any = list.keyOf(id)

  fun name(id: Uuid): String = names[id].orEmpty()

  fun steps(id: Uuid): List<Step> = Step.entries.filter { arranged.stepped(id, it) != null }

  fun move(id: Uuid, step: Step) {
    arranged.stepped(id, step)?.let { moved(it, id) }
  }

  fun drop() {
    lift.drop(settle)?.let { moved(it.order, it.id) }
  }

  fun dismiss() = list.moves.dismiss()

  private fun moved(order: List<Uuid>, id: Uuid) = list.moves.move(order, id, name(id))

  fun handleKey(id: Uuid, key: Key): Boolean {
    val liftedHere = lift.lifted == id
    return when (key) {
      Key.Spacebar, Key.Enter, Key.NumPadEnter -> {
        if (liftedHere) drop() else lift.lift(id, arranged, withKeys = true)
        true
      }
      Key.DirectionUp -> liftedHere.also { if (it) lift.step(Step.Up) }
      Key.DirectionDown -> liftedHere.also { if (it) lift.step(Step.Down) }
      Key.Escape -> liftedHere.also { if (it) lift.putBack() }
      else -> false
    }
  }
}

@Composable
internal fun FollowArrangedOrder(rows: RowOrder) {
  val lift = rows.lift
  LaunchedEffect(lift, rows.arranged) { lift.arrangedChanged() }
  val density = LocalDensity.current
  LaunchedEffect(lift, lift.dragging) {
    if (lift.dragging) with(density) { lift.scrollWhileDragging(ScrollEdge.toPx(), FastestScroll.toPx()) }
  }
}

@Composable
internal fun LazyItemScope.reorderRow(rows: RowOrder, id: Uuid): Modifier {
  val lift = rows.lift
  val raised by animateFloatAsState(if (lift.lifted == id) 1f else 0f, GlorianaTheme.motion.standard.tween())
  val elevated = GlorianaTheme.shadows.elevated
  val shape = GlorianaTheme.shapes.control
  val actions = moveActions(rows, id)
  val followsFinger = (lift.lifted == id && !lift.byKeys) || lift.settling == id
  val placement = if (followsFinger) {
    Modifier.zIndex(1f).graphicsLayer { translationY = lift.translation(id) }
  } else {
    listMotion()
  }
  return placement
    .lookShadow(elevated.copy(elevation = elevated.elevation * raised), shape)
    .background(GlorianaTheme.colors.raised.copy(alpha = raised), shape)
    .semantics { customActions = actions }
}

@Composable
private fun moveActions(rows: RowOrder, id: Uuid): List<CustomAccessibilityAction> {
  val labels = mapOf(Step.Up to stringResource(Res.string.move_up), Step.Down to stringResource(Res.string.move_down))
  return rows.steps(id).map { step ->
    CustomAccessibilityAction(labels.getValue(step)) {
      rows.move(id, step)
      true
    }
  }
}

@Composable
internal fun HandleThenAvatar(rows: RowOrder, id: Uuid, modifier: Modifier = Modifier, avatar: @Composable () -> Unit) {
  val motion = GlorianaTheme.motion
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    AnimatedVisibility(
      rows.active,
      enter = fadeIn(motion.standard.tween()) + expandHorizontally(motion.standard.tween()),
      exit = fadeOut(motion.standard.tween()) + shrinkHorizontally(motion.standard.tween()),
    ) { ReorderHandle(rows, id) }
    avatar()
  }
}

@Composable
private fun ReorderHandle(rows: RowOrder, id: Uuid) {
  val current by rememberUpdatedState(rows)
  val interaction = remember { MutableInteractionSource() }
  val bringIntoView = remember { BringIntoViewRequester() }
  val liftedByKeys = rows.lift.lifted == id && rows.lift.byKeys
  LaunchedEffect(liftedByKeys, rows.shown.indexOf(id)) { if (liftedByKeys) bringIntoView.bringIntoView() }
  val label = stringResource(Res.string.reorder_move_row, rows.name(id))
  val actions = moveActions(rows, id)
  Box(
    Modifier
      .size(HandleWidth, HandleHeight)
      .bringIntoViewRequester(bringIntoView)
      .focusRing(interaction, GlorianaTheme.shapes.control)
      .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && current.handleKey(id, event.key) }
      .focusable(interactionSource = interaction)
      .pointerInput(id) {
        detectDragGestures(
          onDragStart = { current.lift.lift(id, current.arranged, withKeys = false) },
          onDrag = { change, amount ->
            change.consume()
            current.lift.drag(amount.y)
          },
          onDragEnd = { current.drop() },
          onDragCancel = { current.lift.putBack() },
        )
      }
      .semantics {
        contentDescription = label
        role = Role.Button
        customActions = actions
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painterResource(Res.drawable.ic_drag_indicator),
      null,
      Modifier.size(HandleIcon),
      tint = GlorianaTheme.colors.textSubtle,
    )
  }
}

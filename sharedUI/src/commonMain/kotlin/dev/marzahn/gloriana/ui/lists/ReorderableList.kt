package dev.marzahn.gloriana.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.listMotion
import dev.marzahn.gloriana.ui.navigation.ReorderControl
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import kotlin.uuid.Uuid

private const val FEWEST_ROWS_TO_REORDER = 2

private const val NOT_MOVED_KEY = "not-moved"

@Stable
class ReorderableList internal constructor(
  private val mode: ReorderMode,
  internal val moves: UserOrderMoves<*>,
  val listState: LazyListState,
  internal val lift: RowLift,
  internal val keyOf: (Uuid) -> Any,
  notMoved: State<NotMoved?>,
  saved: State<SavedMove?>,
) {
  val active: Boolean get() = mode.active

  internal val notMoved: NotMoved? by notMoved

  internal val saved: SavedMove? by saved

  private var itemKeys: List<Any?> = emptyList()

  internal fun indexOf(key: Any): Int = itemKeys.indexOf(key)

  internal fun laidOut(keys: List<Any?>) {
    itemKeys = keys
  }

  fun control(kind: String, rowCount: Int): ReorderControl? =
    ReorderControl(kind, active, mode::start, mode::end, ::endOnEscape)
      .takeIf { active || rowCount >= FEWEST_ROWS_TO_REORDER }

  private fun endOnEscape(): Boolean = (active && lift.lifted == null).also { if (it) mode.end() }
}

@Composable
fun rememberReorderableList(moves: UserOrderMoves<*>, keyOf: (Uuid) -> Any): ReorderableList {
  val mode = rememberReorderMode()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val currentKeyOf by rememberUpdatedState(keyOf)
  val notMoved = moves.notMoved.collectAsStateWithLifecycle()
  val saved = moves.saved.collectAsStateWithLifecycle()
  LaunchedEffect(moves, mode.active) { if (!mode.active) moves.dismiss() }
  DisposableEffect(moves) { onDispose { moves.dismiss() } }
  return remember(mode, moves, listState, scope, notMoved, saved) {
    val keyOfRow: (Uuid) -> Any = { currentKeyOf(it) }
    ReorderableList(mode, moves, listState, RowLift(listState, keyOfRow, scope), keyOfRow, notMoved, saved)
  }
}

@Composable
fun <T> ReorderableColumn(
  list: ReorderableList,
  rows: List<T>,
  identity: RowIdentity<T>,
  contentPadding: PaddingValues,
  modifier: Modifier = Modifier,
  content: ReorderableListScope<T>.() -> Unit,
) {
  val order = RowOrder(
    list,
    rows.map(identity.id),
    rows.associate { identity.id(it) to identity.name(it) },
    GlorianaTheme.motion.standard.tween(),
  )
  FollowArrangedOrder(order)
  val keys = ItemKeys().apply { ReorderableListScope(this, order, rows, identity.id).content() }.keys
  SideEffect { list.laidOut(keys) }
  Box(modifier) {
    LazyColumn(Modifier.fillMaxSize(), state = list.listState, contentPadding = contentPadding) {
      ReorderableListScope(this, order, rows, identity.id).content()
    }
    ReorderAnnouncement(order, list.saved)
  }
}

class RowIdentity<T>(val id: (T) -> Uuid, val name: (T) -> String)

class ReorderableListScope<T> internal constructor(
  scope: LazyListScope,
  private val order: RowOrder,
  private val rows: List<T>,
  private val id: (T) -> Uuid,
) : LazyListScope by scope {
  val reordering: Boolean get() = order.active

  fun reorderableRows(row: @Composable LazyItemScope.(T, ReorderRow) -> Unit) {
    val rowOrder = order
    val idOf = id
    rowOrder.notMoved?.let { notMoved ->
      item(key = NOT_MOVED_KEY) { NotMovedNote(notMoved, rowOrder::dismiss, listMotion()) }
    }
    val byId = rows.associateBy(idOf)
    items(rowOrder.shown.mapNotNull(byId::get), key = { rowOrder.keyOf(idOf(it)) }) { shown ->
      val rowId = idOf(shown)
      row(shown, ReorderRow(rowOrder, rowId, reorderRow(rowOrder, rowId)))
    }
  }
}

class ReorderRow internal constructor(private val order: RowOrder, private val id: Uuid, val modifier: Modifier) {
  fun onClick(open: () -> Unit): (() -> Unit)? = if (order.active) null else open

  fun trailing(content: (@Composable () -> Unit)?): (@Composable () -> Unit)? = if (order.active) null else content

  fun avatar(content: @Composable () -> Unit): @Composable () -> Unit = {
    HandleThenAvatar(order, id, avatar = content)
  }
}

package dev.marzahn.gloriana.ui.lists

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Stable
internal class FingerPlace(private val listState: LazyListState, private val keyOf: (Uuid) -> Any) {
  private var anchor by mutableFloatStateOf(0f)
  private var awaitedIndex: Int? = null

  fun start(id: Uuid) {
    anchor = item(id)?.offset?.toFloat() ?: 0f
    awaitedIndex = null
  }

  fun moveBy(dy: Float) {
    anchor += dy
  }

  fun offsetOf(id: Uuid): Float = item(id)?.let { anchor - it.offset } ?: 0f

  fun edgeSpeed(id: Uuid, edge: Float, fastest: Float): Float {
    val row = item(id) ?: return 0f
    val layout = listState.layoutInfo
    val pastEnd = anchor + row.size - (layout.viewportEndOffset - edge)
    val pastStart = layout.viewportStartOffset + edge - anchor
    return when {
      pastEnd > 0 -> fastest * (pastEnd / edge).coerceAtMost(1f)
      pastStart > 0 -> -fastest * (pastStart / edge).coerceAtMost(1f)
      else -> 0f
    }
  }

  fun nextStep(order: List<Uuid>, id: Uuid): Step? {
    val row = item(id)?.takeIf { awaitedIndex == null || awaitedIndex == it.index } ?: return null
    awaitedIndex = null
    return stepToward(order, id, row)?.also { awaitedIndex = row.index + it.by }
  }

  private fun stepToward(order: List<Uuid>, id: Uuid, row: LazyListItemInfo): Step? {
    val at = order.indexOf(id)
    val below = order.getOrNull(at + 1)?.let(::item)
    val above = order.getOrNull(at - 1)?.let(::item)
    return when {
      below != null && anchor + row.size > below.middle -> Step.Down
      above != null && anchor < above.middle -> Step.Up
      else -> null
    }
  }

  private fun item(id: Uuid): LazyListItemInfo? {
    val key = keyOf(id)
    return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
  }
}

@Stable
internal class Settle(private val scope: CoroutineScope) {
  var id by mutableStateOf<Uuid?>(null)
    private set
  private val offset = Animatable(0f)

  fun offsetOf(row: Uuid): Float = if (row == id) offset.value else 0f

  fun from(row: Uuid, start: Float, spec: AnimationSpec<Float>) {
    id = row
    scope.launch {
      offset.snapTo(start)
      offset.animateTo(0f, spec)
      if (id == row) id = null
    }
  }
}

private val LazyListItemInfo.middle: Float get() = offset + size / 2f

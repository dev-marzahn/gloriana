package dev.marzahn.gloriana.ui.lists

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlin.uuid.Uuid

enum class Step(val by: Int) { Up(-1), Down(1) }

class Drop(val id: Uuid, val order: List<Uuid>)

sealed interface Spoken {
  val id: Uuid

  class Lifted(override val id: Uuid) : Spoken

  class Stepped(override val id: Uuid) : Spoken
}

fun List<Uuid>.stepped(id: Uuid, step: Step): List<Uuid>? {
  val from = indexOf(id)
  val to = from + step.by
  return if (from < 0 || to !in indices) null else toMutableList().apply { add(to, removeAt(from)) }
}

@Stable
class RowLift(
  private val listState: LazyListState,
  keyOf: (Uuid) -> Any,
  scope: CoroutineScope,
) {
  var lifted by mutableStateOf<Uuid?>(null)
    private set
  var byKeys by mutableStateOf(false)
    private set
  var spoken by mutableStateOf<Spoken?>(null)
    private set
  private var working by mutableStateOf<List<Uuid>?>(null)
  private var liftedFrom: List<Uuid> = emptyList()
  private val finger = FingerPlace(listState, keyOf)
  private val settle = Settle(scope)

  val settling: Uuid? get() = settle.id

  val dragging: Boolean get() = lifted != null && !byKeys

  fun shown(arranged: List<Uuid>): List<Uuid> = working ?: arranged

  fun arrangedChanged() {
    if (lifted == null) working = null
  }

  fun lift(id: Uuid, arranged: List<Uuid>, withKeys: Boolean) {
    lifted = id
    byKeys = withKeys
    liftedFrom = arranged
    working = arranged
    if (withKeys) spoken = Spoken.Lifted(id) else finger.start(id)
  }

  fun drag(dy: Float) {
    finger.moveBy(dy)
    followFinger()
  }

  fun step(step: Step) {
    val id = lifted ?: return
    working?.stepped(id, step)?.let {
      working = it
      spoken = Spoken.Stepped(id)
    }
  }

  fun translation(id: Uuid): Float = if (id == lifted && !byKeys) finger.offsetOf(id) else settle.offsetOf(id)

  fun drop(spec: AnimationSpec<Float>): Drop? {
    val id = lifted ?: return null
    val order = working.orEmpty()
    val changed = order != liftedFrom
    if (!byKeys) settle.from(id, translation(id), spec)
    lifted = null
    if (!changed) working = null
    return Drop(id, order).takeIf { changed }
  }

  fun putBack() {
    lifted = null
    working = null
  }

  suspend fun scrollWhileDragging(edge: Float, fastest: Float) {
    while (dragging) {
      withFrameNanos { }
      val speed = lifted?.let { finger.edgeSpeed(it, edge, fastest) } ?: 0f
      if (speed != 0f) {
        listState.scrollBy(speed)
        followFinger()
      }
    }
  }

  private fun followFinger() {
    val id = lifted ?: return
    val order = working ?: return
    finger.nextStep(order, id)?.let { working = order.stepped(id, it) }
  }
}

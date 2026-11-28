package dev.marzahn.gloriana.ui.lists

import dev.marzahn.gloriana.ui.components.NotDeleted

sealed interface ItemActivity {
  data object Idle : ItemActivity

  data class ConfirmingDelete(val impact: DeleteImpact) : ItemActivity

  data class Deleting(val impact: DeleteImpact) : ItemActivity
}

data class ItemWork(
  val activity: ItemActivity = ItemActivity.Idle,
  val notDeleted: NotDeleted? = null,
  val notMadeDefault: Boolean = false,
) {
  val isIdle: Boolean get() = activity == ItemActivity.Idle

  val holdsNothing: Boolean get() = this == ItemWork()

  val askedImpact: DeleteImpact?
    get() = when (activity) {
      is ItemActivity.ConfirmingDelete -> activity.impact
      is ItemActivity.Deleting -> activity.impact
      ItemActivity.Idle -> null
    }

  fun closed(): ItemWork = ItemWork(activity = activity.takeIf { it is ItemActivity.Deleting } ?: ItemActivity.Idle)
}

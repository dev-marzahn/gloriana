package dev.marzahn.gloriana.ui.lists

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import dev.marzahn.gloriana.ui.components.NotDeleted
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_deleted
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

interface DefaultedLibrary {
  suspend fun idsInListOrder(): List<Uuid>

  suspend fun chatsUsing(id: Uuid): Int

  suspend fun defaultName(): String?

  suspend fun makeDefault(id: Uuid): MakeDefaultResult

  suspend fun delete(id: Uuid): NotDeleted?
}

interface ItemWorkSessions {
  fun <R : Any> claimWork(id: Uuid, change: (work: ItemWork, canAct: Boolean) -> Pair<ItemWork, R>?): R?

  fun deleted(id: Uuid)
}

class DefaultAndDelete(
  private val library: DefaultedLibrary,
  private val sessions: ItemWorkSessions,
  private val deletions: LastDeleted,
  private val scope: ApplicationScope,
  private val messages: AppMessages,
  private val madeDefault: StringResource,
) {
  fun makeDefault(id: Uuid, name: String) {
    claimWhenActing(id) { it.copy(notMadeDefault = false) } ?: return
    scope.launch {
      when (library.makeDefault(id)) {
        MakeDefaultResult.Made -> messages.show(AppMessage(getString(madeDefault, name)))
        MakeDefaultResult.Gone, MakeDefaultResult.Failed -> changeWork(id) { it.copy(notMadeDefault = true) }
      }
    }
  }

  fun askToDelete(id: Uuid) {
    claimWhenActing(id) { it } ?: return
    scope.launch {
      val impact = readDeleteImpact({ library.chatsUsing(id) }, library::defaultName)
      claimWhenActing(id) { it.copy(activity = ItemActivity.ConfirmingDelete(impact), notDeleted = null) }
    }
  }

  fun cancelDelete(id: Uuid) = changeWork(id) { work ->
    if (work.activity is ItemActivity.ConfirmingDelete) work.copy(activity = ItemActivity.Idle) else work
  }

  fun confirmDelete(id: Uuid, name: String) {
    sessions.claimWork(id) { work, _ ->
      (work.activity as? ItemActivity.ConfirmingDelete)?.let { confirming ->
        work.copy(activity = ItemActivity.Deleting(confirming.impact)) to confirming
      }
    } ?: return
    scope.launch {
      val index = failingAs(emptyList()) { library.idsInListOrder() }.indexOf(id)
      when (val refused = library.delete(id)) {
        null -> {
          sessions.deleted(id)
          deletions.record(Deleted(id, index.coerceAtLeast(0)))
          messages.show(AppMessage(getString(Res.string.snack_deleted, name)))
        }
        else -> changeWork(id) { it.copy(activity = ItemActivity.Idle, notDeleted = refused) }
      }
    }
  }

  private fun claimWhenActing(id: Uuid, change: (ItemWork) -> ItemWork): Unit? =
    sessions.claimWork(id) { work, canAct -> if (canAct) change(work) to Unit else null }

  private fun changeWork(id: Uuid, change: (ItemWork) -> ItemWork) {
    sessions.claimWork(id) { work, _ -> change(work) to Unit }
  }
}

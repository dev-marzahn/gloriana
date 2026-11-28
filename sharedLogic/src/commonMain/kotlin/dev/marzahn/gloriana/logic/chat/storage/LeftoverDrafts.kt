package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.app.ProblemLog
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.writeTransaction

internal class LeftoverDrafts(
  private val database: GlorianaDatabase,
  private val dao: SwipeDraftDao,
  private val settling: DraftSettling,
  private val log: ProblemLog,
) {
  suspend fun keepAsUnfinished(): Boolean = dao.all().map { keptOrLogged(it) }.all { it }

  private suspend fun keptOrLogged(draft: SwipeDraftEntity): Boolean = failingAs({ cause ->
    log.record("A leftover draft could not be kept as unfinished", cause)
    false
  }) {
    database.writeTransaction { settling.settle(draft, SwipeState.Unfinished) }
    true
  }
}

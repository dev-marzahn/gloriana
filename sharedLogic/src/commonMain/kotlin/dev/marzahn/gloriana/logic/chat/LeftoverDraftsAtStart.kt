package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.chat.storage.LeftoverDrafts
import dev.marzahn.gloriana.logic.storage.DatabaseOpenedAtStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class LeftoverDraftsAtStart internal constructor(
  drafts: LeftoverDrafts,
  database: DatabaseOpenedAtStart,
  scope: ApplicationScope,
) {
  val kept: Deferred<Boolean> = scope.async {
    database.opened.join()
    failingAs(false) { drafts.keepAsUnfinished() }
  }
}

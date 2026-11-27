package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.app.failingAs
import kotlin.uuid.Uuid

internal class StartSweep(private val dataKeys: DataKeys, private val stored: StoredConnections) {
  private var due = true

  suspend fun runIfDue(holder: KeysetHolder) {
    if (!due) return
    val storedIds = failingAs<Set<Uuid>?>(null) { stored.ids() } ?: return
    failingAs(Unit) {
      holder.withFiles {
        if (due) {
          dataKeys.sweep(storedIds)
          due = false
        }
      }
    }
  }

  fun giveUpForThisRun() {
    due = false
  }
}

package dev.marzahn.gloriana.logic.storage

import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.app.ApplicationScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DatabaseOpenedAtStart(database: GlorianaDatabase, scope: ApplicationScope) {
  val opened: Job = scope.launch { database.useWriterConnection { } }
}

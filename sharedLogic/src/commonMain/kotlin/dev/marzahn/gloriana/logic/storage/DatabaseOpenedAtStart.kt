package dev.marzahn.gloriana.logic.storage

import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.app.ApplicationScope
import kotlinx.coroutines.launch

class DatabaseOpenedAtStart(database: GlorianaDatabase, scope: ApplicationScope) {
  init {
    scope.launch { database.useWriterConnection { } }
  }
}

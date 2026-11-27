package dev.marzahn.gloriana.logic.storage

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

suspend fun <T> GlorianaDatabase.writeTransaction(write: suspend () -> T): T =
  useWriterConnection { connection -> connection.immediateTransaction { write() } }

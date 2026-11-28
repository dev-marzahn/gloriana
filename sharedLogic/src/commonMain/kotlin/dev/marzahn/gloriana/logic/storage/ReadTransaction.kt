package dev.marzahn.gloriana.logic.storage

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection

suspend fun <T> GlorianaDatabase.readTransaction(read: suspend () -> T): T =
  useReaderConnection { connection -> connection.deferredTransaction { read() } }

package dev.marzahn.gloriana.logic.assets

class StorageFullException(cause: Throwable) : Exception(cause)

private val STORAGE_FULL_MESSAGES = listOf(
  "No space left on device",
  "ENOSPC",
  "not enough space on the disk",
  "SQLITE_FULL",
  "database or disk is full",
)

fun Throwable.isStorageFull(): Boolean =
  this is StorageFullException || generateSequence(this) { it.cause }.any { error ->
    STORAGE_FULL_MESSAGES.any { error.message.orEmpty().contains(it, ignoreCase = true) }
  }

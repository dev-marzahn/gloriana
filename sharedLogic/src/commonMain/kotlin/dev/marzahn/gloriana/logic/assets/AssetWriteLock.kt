package dev.marzahn.gloriana.logic.assets

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AssetWriteLock {
  private val mutex = Mutex()

  suspend fun <T> whileImporting(work: suspend () -> T): T = mutex.withLock { work() }

  suspend fun <T> unlessImporting(work: suspend () -> T): T? {
    if (!mutex.tryLock()) return null
    return try {
      work()
    } finally {
      mutex.unlock()
    }
  }
}

fun interface AssetReferences {
  suspend fun referencedHashes(): Set<AssetHash>
}

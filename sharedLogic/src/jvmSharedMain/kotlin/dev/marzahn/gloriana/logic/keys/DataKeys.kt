package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.Aead
import java.nio.file.Path
import kotlin.uuid.Uuid

internal class DataKeys(path: Path) {
  private val file = DataKeyFile(path)
  private val opened = mutableMapOf<Uuid, Aead>()
  private val madeThisRun = mutableSetOf<Uuid>()

  fun find(connectionId: Uuid, keyset: Aead): Aead? = opened[connectionId]
    ?: file.read()[connectionId]
      ?.let { filed -> Keysets.unwrap(filed, keyset, connectionId.toByteArray()) }
      ?.let(Keysets::aead)
      ?.also { opened[connectionId] = it }

  fun findOrMake(connectionId: Uuid, keyset: Aead): Aead = find(connectionId, keyset) ?: make(connectionId, keyset)

  fun erase(connectionId: Uuid) {
    opened -= connectionId
    madeThisRun -= connectionId
    val filed = file.read()
    if (connectionId in filed) file.write(filed - connectionId)
  }

  fun sweep(storedConnections: Set<Uuid>) {
    val filed = file.read()
    val kept = filed.filterKeys { it in storedConnections || it in madeThisRun }
    opened.keys.retainAll(kept.keys)
    if (kept.size != filed.size) file.write(kept)
  }

  fun eraseAll() {
    file.write(emptyMap())
    opened.clear()
    madeThisRun.clear()
  }

  private fun make(connectionId: Uuid, keyset: Aead): Aead {
    val dataKey = Keysets.newAes256Gcm()
    file.write(file.read() + (connectionId to Keysets.wrap(dataKey, keyset, connectionId.toByteArray())))
    madeThisRun += connectionId
    return Keysets.aead(dataKey).also { opened[connectionId] = it }
  }
}

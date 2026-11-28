package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path
import kotlin.uuid.Uuid

internal class DataKeys(path: Path) {
  private val file = DataKeyFile(path)
  private val opened = mutableMapOf<Uuid, Aead>()
  private val known = MutableStateFlow<FiledDataKeys>(FiledDataKeys.NotReadYet)

  val filed: StateFlow<FiledDataKeys> = known.asStateFlow()

  fun find(connectionId: Uuid, keyset: Aead): Aead? =
    opened[connectionId] ?: unwrapped(file.read(), connectionId, keyset)

  fun holdsAny(): Boolean = file.read().isNotEmpty()

  fun findOrMake(connectionId: Uuid, keyset: Aead): Aead {
    val filed = file.read()
    publish(filed.keys)
    return opened[connectionId] ?: unwrapped(filed, connectionId, keyset) ?: make(filed, connectionId, keyset)
  }

  fun erase(connectionId: Uuid) {
    opened -= connectionId
    val filed = file.read()
    if (connectionId in filed) write(filed - connectionId)
  }

  fun sweep(storedConnections: Set<Uuid>) {
    val filed = file.read()
    val kept = filed.filterKeys { it in storedConnections }
    opened.keys.retainAll(kept.keys)
    if (kept.size != filed.size) write(kept) else publish(kept.keys)
  }

  fun eraseAll() {
    write(emptyMap())
    opened.clear()
  }

  private fun unwrapped(filed: Map<Uuid, ByteArray>, connectionId: Uuid, keyset: Aead): Aead? =
    filed[connectionId]
      ?.let { wrapped -> Keysets.unwrap(wrapped, keyset, connectionId.toByteArray()) }
      ?.let(Keysets::aead)
      ?.also { opened[connectionId] = it }

  private fun make(filed: Map<Uuid, ByteArray>, connectionId: Uuid, keyset: Aead): Aead {
    val dataKey = Keysets.newAes256Gcm()
    write(filed + (connectionId to Keysets.wrap(dataKey, keyset, connectionId.toByteArray())))
    return Keysets.aead(dataKey).also { opened[connectionId] = it }
  }

  private fun write(keys: Map<Uuid, ByteArray>) {
    file.write(keys)
    publish(keys.keys)
  }

  private fun publish(connections: Set<Uuid>) {
    known.value = FiledDataKeys.Read(connections)
  }
}

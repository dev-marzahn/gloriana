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

  fun find(connectionId: Uuid, keyset: Aead): Aead? = opened[connectionId]
    ?: read()[connectionId]
      ?.let { filed -> Keysets.unwrap(filed, keyset, connectionId.toByteArray()) }
      ?.let(Keysets::aead)
      ?.also { opened[connectionId] = it }

  fun findOrMake(connectionId: Uuid, keyset: Aead): Aead = find(connectionId, keyset) ?: make(connectionId, keyset)

  fun erase(connectionId: Uuid) {
    opened -= connectionId
    val filed = read()
    if (connectionId in filed) write(filed - connectionId)
  }

  fun sweep(storedConnections: Set<Uuid>) {
    val filed = read()
    val kept = filed.filterKeys { it in storedConnections }
    opened.keys.retainAll(kept.keys)
    if (kept.size != filed.size) write(kept)
  }

  fun eraseAll() {
    write(emptyMap())
    opened.clear()
  }

  private fun make(connectionId: Uuid, keyset: Aead): Aead {
    val dataKey = Keysets.newAes256Gcm()
    write(read() + (connectionId to Keysets.wrap(dataKey, keyset, connectionId.toByteArray())))
    return Keysets.aead(dataKey).also { opened[connectionId] = it }
  }

  private fun read(): Map<Uuid, ByteArray> = file.read().also { known.value = FiledDataKeys.Read(it.keys) }

  private fun write(keys: Map<Uuid, ByteArray>) {
    file.write(keys)
    known.value = FiledDataKeys.Read(keys.keys)
  }
}

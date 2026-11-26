package dev.marzahn.gloriana.logic.storage

import androidx.room.TypeConverter
import kotlin.uuid.Uuid

internal object StorageConverters {
  @TypeConverter
  fun uuidToBytes(id: Uuid): ByteArray = id.toByteArray()

  @TypeConverter
  fun bytesToUuid(bytes: ByteArray): Uuid = Uuid.fromByteArray(bytes)

  @TypeConverter
  fun rowKeyToBytes(key: RowKey): ByteArray = when (key) {
    is RowKey.Row -> key.id.toByteArray()
    is RowKey.JoinRow -> key.first.toByteArray() + key.second.toByteArray()
  }

  @TypeConverter
  fun bytesToRowKey(bytes: ByteArray): RowKey {
    require(bytes.size == Uuid.SIZE_BYTES || bytes.size == 2 * Uuid.SIZE_BYTES) {
      "A row key is one or two ids, not ${bytes.size} bytes"
    }
    return if (bytes.size == Uuid.SIZE_BYTES) {
      RowKey.Row(Uuid.fromByteArray(bytes))
    } else {
      RowKey.JoinRow(
        first = Uuid.fromByteArray(bytes.copyOfRange(0, Uuid.SIZE_BYTES)),
        second = Uuid.fromByteArray(bytes.copyOfRange(Uuid.SIZE_BYTES, bytes.size)),
      )
    }
  }
}

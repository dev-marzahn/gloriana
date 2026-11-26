package dev.marzahn.gloriana.logic.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageConvertersTest {
  @Test
  fun aUuidV7RoundTripsAsSixteenBytes() {
    val id = UuidV7IdFactory.newId()

    val bytes = StorageConverters.uuidToBytes(id)

    assertEquals(16, bytes.size)
    assertEquals(7, (bytes[6].toInt() shr 4) and 0x0F)
    assertEquals(id, StorageConverters.bytesToUuid(bytes))
  }

  @Test
  fun aRowsKeyIsItsId() {
    val id = UuidV7IdFactory.newId()

    val bytes = StorageConverters.rowKeyToBytes(RowKey.Row(id))

    assertContentEquals(StorageConverters.uuidToBytes(id), bytes)
    assertEquals(RowKey.Row(id), StorageConverters.bytesToRowKey(bytes))
  }

  @Test
  fun aJoinRowsKeyIsItsTwoIdsInOrder() {
    val key = RowKey.JoinRow(UuidV7IdFactory.newId(), UuidV7IdFactory.newId())

    val bytes = StorageConverters.rowKeyToBytes(key)

    assertEquals(32, bytes.size)
    assertEquals(key, StorageConverters.bytesToRowKey(bytes))
  }

  @Test
  fun aKeyOfAnyOtherLengthIsRefused() {
    assertFailsWith<IllegalArgumentException> { StorageConverters.bytesToRowKey(ByteArray(20)) }
  }
}

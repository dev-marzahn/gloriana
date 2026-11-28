package dev.marzahn.gloriana.logic.settings

import dev.marzahn.gloriana.logic.json.decodedOrNull
import dev.marzahn.gloriana.logic.settings.storage.LibrarySettingEntity
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.StoredJson
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

class LibrarySetting<T : Any>(val key: String, val serializer: KSerializer<T>)

class LibrarySettings(private val database: GlorianaDatabase, private val clock: UpdatedAtClock) {
  private val dao = database.librarySettingDao()

  fun <T : Any> observe(setting: LibrarySetting<T>): Flow<T?> =
    dao.observe(setting.key).map { it?.value?.let(setting::decodeOrNull) }.distinctUntilChanged()

  suspend fun <T : Any> read(setting: LibrarySetting<T>): T? = dao.find(setting.key)?.value?.let(setting::decodeOrNull)

  suspend fun <T : Any> write(setting: LibrarySetting<T>, value: T?) {
    database.writeTransaction {
      val previous = dao.find(setting.key)
      dao.upsert(
        LibrarySettingEntity(
          key = setting.key,
          value = value?.let { StoredJson.encodeToString(setting.serializer, it) },
          updatedAt = clock.updatedAt(previous?.updatedAt),
        ),
      )
    }
  }
}

private fun <T : Any> LibrarySetting<T>.decodeOrNull(stored: String): T? =
  StoredJson.decodedOrNull(serializer, stored)

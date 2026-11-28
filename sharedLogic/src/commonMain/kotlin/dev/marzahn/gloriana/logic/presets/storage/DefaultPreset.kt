package dev.marzahn.gloriana.logic.presets.storage

import dev.marzahn.gloriana.logic.presets.file.PresetFileRead
import dev.marzahn.gloriana.logic.presets.file.PresetJsonReader
import dev.marzahn.gloriana.logic.settings.AlwaysSetDefault
import dev.marzahn.gloriana.logic.settings.DefaultRow
import dev.marzahn.gloriana.logic.settings.LibrarySetting
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.serializer
import kotlin.uuid.Uuid

internal val DefaultPresetSetting = LibrarySetting("defaultPreset", Uuid.serializer())

internal const val SHIPPED_PRESET_NAME = "Default"

internal class DefaultPreset(
  database: GlorianaDatabase,
  settings: LibrarySettings,
  dao: PresetDao,
  private val writes: PresetWrites,
  private val reader: PresetJsonReader,
) {
  private val default =
    AlwaysSetDefault(database, DefaultRow(database, settings, DefaultPresetSetting, dao.observeIds(), dao::exists))

  fun observe(): Flow<Uuid?> = default.observe()

  suspend fun make(id: Uuid): MakeDefaultResult = default.make(id)

  suspend fun <T> unlessDefault(id: Uuid, refused: T, write: suspend (default: Uuid?) -> T): T =
    default.unlessDefault(id, refused, write)

  suspend fun ensureShipped() = default.ensure {
    val shipped = reader.read(SHIPPED_PRESET_JSON)
    check(shipped is PresetFileRead.Read) { "The shipped Preset reads as $shipped" }
    writes.insert(SHIPPED_PRESET_NAME, shipped.content, shipped.source)
  }
}

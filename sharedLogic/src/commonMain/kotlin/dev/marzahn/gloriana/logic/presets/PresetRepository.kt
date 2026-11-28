package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportWrite
import dev.marzahn.gloriana.logic.files.writeAll
import dev.marzahn.gloriana.logic.files.writeOnIo
import dev.marzahn.gloriana.logic.presets.file.PresetJsonWriter
import dev.marzahn.gloriana.logic.presets.storage.DecodedPreset
import dev.marzahn.gloriana.logic.presets.storage.DefaultPreset
import dev.marzahn.gloriana.logic.presets.storage.PresetDao
import dev.marzahn.gloriana.logic.presets.storage.PresetWrites
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.Reordering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class PresetRepository internal constructor(
  private val dao: PresetDao,
  private val writes: PresetWrites,
  private val ids: IdFactory,
  private val chats: ChatsUsing,
  private val default: DefaultPreset,
) {
  fun presets(): Flow<List<PresetSummary>> =
    dao.summaries().map { rows -> rows.map { PresetSummary(it.id, it.name, it.entries, it.entriesOn) } }

  fun preset(id: Uuid): Flow<StoredPreset?> = dao.observeRows(id).map { it?.decoded()?.stored }

  suspend fun exists(id: Uuid): Boolean = dao.exists(id)

  fun newGroup(name: String): PresetGroup = PresetGroup(ids.newId(), name)

  suspend fun chatsUsing(id: Uuid): Int = chats.count(ChatSetting.Preset, id)

  suspend fun save(preset: Preset): PresetSaveResult =
    failingAs({ if (it.isStorageFull()) PresetSaveResult.StorageFull else PresetSaveResult.Failed }) {
      writes.save(preset)
    }

  val order: Reordering get() = writes.order

  fun defaultPreset(): Flow<Uuid?> = default.observe()

  suspend fun defaultName(): String? = default.name()

  suspend fun makeDefault(id: Uuid): MakeDefaultResult = failingAs(MakeDefaultResult.Failed) { default.make(id) }

  suspend fun delete(id: Uuid): PresetDeleteResult = failingAs(PresetDeleteResult.Failed) {
    default.unlessDefault(id, refused = PresetDeleteResult.IsDefault) { writes.delete(id, chatsMoveTo = it) }
  }

  suspend fun export(id: Uuid, destination: ExportDestination): PresetExportResult =
    when (val decoded = dao.rows(id)?.decoded()) {
      null -> PresetExportResult.Gone
      DecodedPreset.Unreadable -> PresetExportResult.Unreadable
      is DecodedPreset.Readable -> destination.writeOnIo { sink -> sink.writeAll(decoded.encoded()) }.asPresetExport()
    }
}

private fun DecodedPreset.Readable.encoded() = PresetJsonWriter.encode(preset.content, source)

private fun ExportWrite.asPresetExport(): PresetExportResult = when (this) {
  ExportWrite.Written -> PresetExportResult.Exported
  is ExportWrite.Failed -> PresetExportResult.Failed(problem)
}

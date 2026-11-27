package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportWrite
import dev.marzahn.gloriana.logic.presets.file.PresetJsonWriter
import dev.marzahn.gloriana.logic.presets.storage.DecodedPreset
import dev.marzahn.gloriana.logic.presets.storage.PresetDao
import dev.marzahn.gloriana.logic.presets.storage.PresetWrites
import dev.marzahn.gloriana.logic.storage.IdFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class PresetRepository internal constructor(
  private val dao: PresetDao,
  private val writes: PresetWrites,
  private val ids: IdFactory,
  private val chats: PresetChats,
) {
  fun presets(): Flow<List<PresetSummary>> =
    dao.summaries().map { rows -> rows.map { PresetSummary(it.id, it.name, it.entries, it.entriesOn) } }

  fun preset(id: Uuid): Flow<StoredPreset?> = dao.observeRows(id).map { it?.decoded()?.stored }

  fun newGroup(name: String): PresetGroup = PresetGroup(ids.newId(), name)

  suspend fun chatsUsing(id: Uuid): Int = chats.count(id)

  suspend fun save(preset: Preset): PresetSaveResult {
    val trimmed = preset.copy(name = preset.name.trim())
    val problems = trimmed.problems()
    if (problems.isNotEmpty()) return PresetSaveResult.Refused(problems)
    return failingAs({ if (it.isStorageFull()) PresetSaveResult.StorageFull else PresetSaveResult.Failed }) {
      writes.save(trimmed)
    }
  }

  suspend fun delete(id: Uuid): PresetDeleteResult = failingAs(PresetDeleteResult.Failed) { writes.delete(id) }

  suspend fun export(id: Uuid, destination: ExportDestination): PresetExportResult =
    when (val decoded = dao.rows(id)?.decoded()) {
      null -> PresetExportResult.Gone
      DecodedPreset.Unreadable -> PresetExportResult.Unreadable
      is DecodedPreset.Readable -> written(PresetJsonWriter.encode(decoded.preset.content, decoded.source), destination)
    }

  private fun written(file: ByteArray, destination: ExportDestination): PresetExportResult =
    when (val written = destination.write { sink -> sink.write(file, 0, file.size) }) {
      ExportWrite.Written -> PresetExportResult.Exported
      is ExportWrite.Failed -> PresetExportResult.Failed(written.problem)
    }
}

private fun Preset.problems(): Set<PresetProblem> {
  val ids = content.rows.map { it.id }
  val repeated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map(PresetProblem::RepeatedRow)
  val negativeDepths = content.entries.filter { it.placement.isNegative() }.map { PresetProblem.NegativeDepth(it.id) }
  return setOfNotNull(PresetProblem.EmptyName.takeIf { name.isEmpty() }) + repeated + negativeDepths
}

private fun EntryPlacement.isNegative(): Boolean = when (this) {
  EntryPlacement.Relative -> false
  is EntryPlacement.InChat -> depth < 0
}

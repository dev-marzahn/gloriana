package dev.marzahn.gloriana.logic.presets.storage

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.logic.presets.WrapperPrompts
import dev.marzahn.gloriana.logic.storage.EntityType
import dev.marzahn.gloriana.logic.storage.StoredJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

internal const val PRESETS_TABLE = "presets"
internal const val PRESET_ENTRIES_TABLE = "preset_entries"
internal const val PRESET_GROUPS_TABLE = "preset_groups"

internal val PresetsEntityType = EntityType(PRESETS_TABLE)
internal val PresetEntriesEntityType = EntityType(PRESET_ENTRIES_TABLE)
internal val PresetGroupsEntityType = EntityType(PRESET_GROUPS_TABLE)

private val EntryRole.storedValue: String
  get() = when (this) {
    EntryRole.System -> "system"
    EntryRole.User -> "user"
    EntryRole.Assistant -> "assistant"
  }

@Entity(tableName = PRESETS_TABLE, indices = [Index("nameKey")])
internal data class PresetEntity(
  @PrimaryKey val id: Uuid,
  val name: String,
  val nameKey: String,
  val wrappers: String,
  val sourceJson: String,
  val updatedAt: Long,
)

internal sealed interface PresetChildEntity {
  val id: Uuid
  val updatedAt: Long
  val entityType: EntityType

  fun stamped(updatedAt: Long): PresetChildEntity
}

@Entity(
  tableName = PRESET_ENTRIES_TABLE,
  foreignKeys = [ForeignKey(entity = PresetEntity::class, parentColumns = ["id"], childColumns = ["presetId"])],
  indices = [Index("presetId")],
)
internal data class PresetEntryEntity(
  @PrimaryKey override val id: Uuid,
  val presetId: Uuid,
  val position: Int,
  val identifier: String,
  val name: String,
  val text: String,
  val filledByApp: Boolean,
  val enabled: Boolean,
  val role: String,
  val inChatDepth: Int?,
  override val updatedAt: Long,
) : PresetChildEntity {
  override val entityType: EntityType get() = PresetEntriesEntityType

  override fun stamped(updatedAt: Long) = copy(updatedAt = updatedAt)
}

@Entity(
  tableName = PRESET_GROUPS_TABLE,
  foreignKeys = [ForeignKey(entity = PresetEntity::class, parentColumns = ["id"], childColumns = ["presetId"])],
  indices = [Index("presetId")],
)
internal data class PresetGroupEntity(
  @PrimaryKey override val id: Uuid,
  val presetId: Uuid,
  val position: Int,
  val name: String,
  override val updatedAt: Long,
) : PresetChildEntity {
  override val entityType: EntityType get() = PresetGroupsEntityType

  override fun stamped(updatedAt: Long) = copy(updatedAt = updatedAt)
}

internal data class PresetRows(
  @Embedded val preset: PresetEntity,
  @Relation(parentColumn = "id", entityColumn = "presetId") val entries: List<PresetEntryEntity>,
  @Relation(parentColumn = "id", entityColumn = "presetId") val groups: List<PresetGroupEntity>,
) {
  val children: List<PresetChildEntity> get() = entries + groups

  fun decoded(): DecodedPreset {
    val wrappers = decodedWrappers(preset.wrappers)
    val source = decodedSource(preset.sourceJson)
    val entryRows = entries.map { entity -> entity.position to entity.toEntry() }
    return if (wrappers == null || source == null || entryRows.any { (_, entry) -> entry == null }) {
      DecodedPreset.Unreadable
    } else {
      val rows = (entryRows.mapNotNull { (position, entry) -> entry?.let { position to it } } +
        groups.map { it.position to PresetGroup(it.id, it.name) })
        .sortedBy { (position) -> position }
        .map { (_, row) -> row }
      DecodedPreset.Readable(Preset(preset.id, preset.name, PresetContent(rows, wrappers)), source)
    }
  }
}

internal sealed interface DecodedPreset {
  val stored: StoredPreset

  class Readable(val preset: Preset, val source: JsonObject) : DecodedPreset {
    override val stored: StoredPreset get() = StoredPreset.Readable(preset)
  }

  data object Unreadable : DecodedPreset {
    override val stored: StoredPreset get() = StoredPreset.Unreadable
  }
}

private fun decodedSource(stored: String): JsonObject? = try {
  StoredJson.decodeFromString<JsonObject>(stored)
} catch (_: SerializationException) {
  null
}

internal data class PresetSummaryRow(val id: Uuid, val name: String)

internal fun storedWrappers(wrappers: WrapperPrompts): String =
  StoredJson.encodeToString(wrappers.changes.mapKeys { (prompt) -> prompt.key })

private val wrappersByKey = WrapperPrompt.entries.associateBy { it.key }

private fun decodedWrappers(stored: String): WrapperPrompts? {
  val texts = try {
    StoredJson.decodeFromString<Map<String, String>>(stored)
  } catch (_: SerializationException) {
    return null
  }
  return if (wrappersByKey.keys.containsAll(texts.keys)) {
    WrapperPrompts.of(texts.mapKeys { (key) -> wrappersByKey.getValue(key) })
  } else {
    null
  }
}

private fun PresetEntryEntity.toEntry(): PresetEntry? =
  EntryRole.entries.firstOrNull { it.storedValue == role }?.let { entryRole ->
    PresetEntry(
      id = id,
      identifier = identifier,
      name = name,
      text = text,
      filledByApp = filledByApp,
      enabled = enabled,
      role = entryRole,
      placement = inChatDepth?.let(EntryPlacement::InChat) ?: EntryPlacement.Relative,
    )
  }

internal fun PresetRow.toEntity(presetId: Uuid, position: Int, updatedAt: Long): PresetChildEntity = when (this) {
  is PresetEntry -> PresetEntryEntity(
    id = id,
    presetId = presetId,
    position = position,
    identifier = identifier,
    name = name,
    text = text,
    filledByApp = filledByApp,
    enabled = enabled,
    role = role.storedValue,
    inChatDepth = (placement as? EntryPlacement.InChat)?.depth,
    updatedAt = updatedAt,
  )
  is PresetGroup -> PresetGroupEntity(id, presetId, position, name, updatedAt)
}

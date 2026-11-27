package dev.marzahn.gloriana.logic.characters.storage

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.marzahn.gloriana.logic.characters.CharacterNote
import dev.marzahn.gloriana.logic.characters.KeptCardFields
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.logic.storage.EntityType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

internal const val CHARACTERS_TABLE = "characters"
internal const val CHARACTER_GREETINGS_TABLE = "character_greetings"
internal const val CHARACTER_ASSETS_TABLE = "character_assets"

internal val CharactersEntityType = EntityType(CHARACTERS_TABLE)
internal val CharacterGreetingsEntityType = EntityType(CHARACTER_GREETINGS_TABLE)
internal val CharacterAssetsEntityType = EntityType(CHARACTER_ASSETS_TABLE)

@Entity(
  tableName = CHARACTERS_TABLE,
  indices = [Index("importedAt"), Index("nameKey", "creatorKey")],
)
@TypeConverters(CharacterConverters::class)
internal data class CharacterEntity(
  @PrimaryKey val id: Uuid,
  val name: String,
  val nickname: String,
  val description: String,
  val personality: String,
  val scenario: String,
  val firstMessage: String,
  val exampleMessages: String,
  val creator: String,
  val characterVersion: String,
  val creatorNotes: String,
  val systemPrompt: String,
  val postHistoryInstructions: String,
  @Embedded(prefix = "note_") val characterNote: CharacterNote?,
  @Embedded(prefix = "pronoun_") val pronouns: Pronouns,
  val avatarHash: String?,
  val keptFields: KeptCardFields,
  val cardJson: String,
  val nameKey: String,
  val creatorKey: String,
  val importedAt: Long,
  val updatedAt: Long,
)

internal enum class GreetingKind { Alternate, GroupOnly }

@Entity(
  tableName = CHARACTER_GREETINGS_TABLE,
  foreignKeys = [
    ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"]),
  ],
  indices = [Index("characterId")],
)
internal data class CharacterGreetingEntity(
  @PrimaryKey val id: Uuid,
  val characterId: Uuid,
  val kind: GreetingKind,
  val position: Int,
  val text: String,
  val updatedAt: Long,
)

@Entity(
  tableName = CHARACTER_ASSETS_TABLE,
  foreignKeys = [
    ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"]),
  ],
  indices = [Index("characterId"), Index("hash")],
)
@TypeConverters(CharacterConverters::class)
internal data class CharacterAssetEntity(
  @PrimaryKey val id: Uuid,
  val characterId: Uuid,
  val position: Int,
  val type: String,
  val name: String,
  val extension: String,
  val hash: String?,
  val uri: String?,
  val otherKeys: JsonObject,
  val updatedAt: Long,
)

internal data class CharacterRows(
  @Embedded val character: CharacterEntity,
  @Relation(parentColumn = "id", entityColumn = "characterId")
  val greetings: List<CharacterGreetingEntity>,
  @Relation(parentColumn = "id", entityColumn = "characterId")
  val assets: List<CharacterAssetEntity>,
)

internal data class CharacterSummaryRow(val id: Uuid, val name: String, val avatarHash: String?)

internal object CharacterConverters {
  @TypeConverter
  fun keptFieldsToText(kept: KeptCardFields): String = Json.encodeToString(kept)

  @TypeConverter
  fun textToKeptFields(text: String): KeptCardFields = Json.decodeFromString(text)

  @TypeConverter
  fun jsonObjectToText(value: JsonObject): String = Json.encodeToString(value)

  @TypeConverter
  fun textToJsonObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}

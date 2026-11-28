package dev.marzahn.gloriana.logic.personas.storage

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.logic.personas.Persona
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.personas.PersonaInPrompt
import dev.marzahn.gloriana.logic.storage.EntityType
import dev.marzahn.gloriana.logic.storage.nameKey
import kotlin.uuid.Uuid

internal const val PERSONAS_TABLE = "personas"

internal val PersonasEntityType = EntityType(PERSONAS_TABLE)

@Entity(tableName = PERSONAS_TABLE, indices = [Index("nameKey")])
internal data class PersonaEntity(
  @PrimaryKey val id: Uuid,
  val name: String,
  val nameKey: String,
  val description: String,
  val personaLabel: String,
  val personaNote: String,
  @Embedded(prefix = "pronoun_") val pronouns: Pronouns,
  val updatedAt: Long,
) {
  val persona: Persona
    get() = Persona(id, PersonaFields(name, description, personaLabel, personaNote, pronouns))
}

internal data class PersonaInPromptRow(
  val name: String,
  val description: String,
  @Embedded(prefix = "pronoun_") val pronouns: Pronouns,
) {
  val inPrompt: PersonaInPrompt get() = PersonaInPrompt(name, description, pronouns)
}

internal fun PersonaFields.toEntity(id: Uuid, updatedAt: Long) = PersonaEntity(
  id = id,
  name = name,
  nameKey = nameKey(name),
  description = description,
  personaLabel = personaLabel,
  personaNote = personaNote,
  pronouns = pronouns,
  updatedAt = updatedAt,
)

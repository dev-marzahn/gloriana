package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.characters.Pronouns
import kotlin.uuid.Uuid

data class PersonaFields(
  val name: String,
  val description: String = "",
  val personaLabel: String = "",
  val personaNote: String = "",
  val pronouns: Pronouns = Pronouns.None,
) {
  internal fun normalised(): PersonaFields = copy(name = name.trim())
}

data class Persona(val id: Uuid, val fields: PersonaFields)

data class PersonaInPrompt(val name: String, val description: String, val pronouns: Pronouns)

sealed interface PersonaSaveResult {
  data class Saved(val id: Uuid) : PersonaSaveResult

  data object EmptyName : PersonaSaveResult

  data object Gone : PersonaSaveResult

  data object StorageFull : PersonaSaveResult

  data object Failed : PersonaSaveResult
}

enum class PersonaDeleteResult {
  Deleted,
  IsDefault,
  Gone,
  Failed,
}

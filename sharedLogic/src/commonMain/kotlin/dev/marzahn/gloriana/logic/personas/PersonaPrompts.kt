package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.personas.storage.PersonaDao
import kotlin.uuid.Uuid

class PersonaPrompts internal constructor(private val dao: PersonaDao) {
  suspend fun persona(id: Uuid): PersonaInPrompt? = dao.findInPrompt(id)?.inPrompt
}

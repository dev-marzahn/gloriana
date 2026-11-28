package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.personas.storage.DefaultPersona
import dev.marzahn.gloriana.logic.personas.storage.PersonaDao
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

class PersonaPrompts internal constructor(private val dao: PersonaDao, private val default: DefaultPersona) {
  suspend fun persona(id: Uuid): PersonaInPrompt? = dao.findInPrompt(id)?.inPrompt

  suspend fun defaultPersonaId(): Uuid? = default.observe().first()
}

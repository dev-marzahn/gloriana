package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.personas.PersonaRepository
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

suspend fun personaLine(personas: PersonaRepository, id: Uuid) =
  personas.persona(id).first()?.fields?.let { (name, _, label) -> "$name, $label" }

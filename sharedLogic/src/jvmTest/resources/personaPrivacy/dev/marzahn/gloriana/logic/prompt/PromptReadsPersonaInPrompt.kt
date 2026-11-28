package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.personas.PersonaInPrompt
import dev.marzahn.gloriana.logic.personas.PersonaPrompts
import kotlin.uuid.Uuid

suspend fun personaLine(prompts: PersonaPrompts, id: Uuid) = prompts.persona(id)?.let(::line)

private fun line(persona: PersonaInPrompt) = "${persona.name}: ${persona.description} $persona"

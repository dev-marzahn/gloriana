package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.personas.Persona

fun systemLine(persona: Persona) = "You speak with ${persona.fields.name} (${persona.fields.personaLabel})."

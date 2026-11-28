package dev.marzahn.gloriana.ui.chat

import dev.marzahn.gloriana.logic.personas.Persona

fun replyContext(persona: Persona, send: (String) -> Unit) = send(persona.fields.toString())

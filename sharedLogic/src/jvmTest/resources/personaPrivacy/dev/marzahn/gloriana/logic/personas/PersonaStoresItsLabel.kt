package dev.marzahn.gloriana.logic.personas

fun labelled(fields: PersonaFields) = fields.copy(personaLabel = fields.personaLabel.trim())

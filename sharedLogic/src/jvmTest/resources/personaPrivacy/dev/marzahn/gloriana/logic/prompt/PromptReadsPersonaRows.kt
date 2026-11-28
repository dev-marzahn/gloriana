package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import kotlin.uuid.Uuid

suspend fun personaLine(database: GlorianaDatabase, id: Uuid) = database.personaDao().find(id).toString()

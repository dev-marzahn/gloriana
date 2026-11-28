package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.personas.storage.DefaultPersona
import dev.marzahn.gloriana.logic.personas.storage.PersonaWrites
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import org.koin.dsl.module

val personasModule = module {
  single { get<GlorianaDatabase>().personaDao() }
  single { PersonaWrites(get(), get(), get(), get(), get(), get(), get()) }
  single { DefaultPersona(get(), get(), get(), get()) }
  single { PersonaRepository(get(), get(), get(), get()) }
  single { PersonaPrompts(get(), get()) }
}

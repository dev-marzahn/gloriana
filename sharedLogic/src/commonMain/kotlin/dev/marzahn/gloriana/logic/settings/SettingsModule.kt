package dev.marzahn.gloriana.logic.settings

import org.koin.dsl.module

val settingsModule = module {
  single { LibrarySettings(get(), get()) }
}

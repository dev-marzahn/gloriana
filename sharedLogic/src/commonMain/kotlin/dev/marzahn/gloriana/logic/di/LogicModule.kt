package dev.marzahn.gloriana.logic.di

import dev.marzahn.gloriana.logic.characters.charactersModule
import dev.marzahn.gloriana.logic.connection.api.modelApiModule
import dev.marzahn.gloriana.logic.connection.connectionsModule
import dev.marzahn.gloriana.logic.debug.debugModule
import dev.marzahn.gloriana.logic.keys.keysModule
import dev.marzahn.gloriana.logic.presets.presetsModule
import dev.marzahn.gloriana.logic.storage.storageModule
import org.koin.dsl.module

val logicModule = module {
  includes(
    storageModule,
    debugModule,
    charactersModule,
    keysModule,
    connectionsModule,
    modelApiModule,
    presetsModule,
  )
}

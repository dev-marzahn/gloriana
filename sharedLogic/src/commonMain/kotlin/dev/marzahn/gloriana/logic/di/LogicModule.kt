package dev.marzahn.gloriana.logic.di

import dev.marzahn.gloriana.logic.app.shippedDefaultsModule
import dev.marzahn.gloriana.logic.characters.charactersModule
import dev.marzahn.gloriana.logic.connection.api.modelApiModule
import dev.marzahn.gloriana.logic.connection.connectionsModule
import dev.marzahn.gloriana.logic.debug.debugModule
import dev.marzahn.gloriana.logic.keys.keysModule
import dev.marzahn.gloriana.logic.personas.personasModule
import dev.marzahn.gloriana.logic.presets.presetsModule
import dev.marzahn.gloriana.logic.settings.settingsModule
import dev.marzahn.gloriana.logic.storage.storageModule
import dev.marzahn.gloriana.logic.tasks.tasksModule
import org.koin.dsl.module

val logicModule = module {
  includes(
    storageModule,
    settingsModule,
    debugModule,
    charactersModule,
    keysModule,
    connectionsModule,
    modelApiModule,
    presetsModule,
    personasModule,
    shippedDefaultsModule,
    tasksModule,
  )
}

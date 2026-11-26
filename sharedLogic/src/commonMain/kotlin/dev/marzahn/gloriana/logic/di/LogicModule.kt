package dev.marzahn.gloriana.logic.di

import dev.marzahn.gloriana.logic.storage.storageModule
import org.koin.dsl.module

val logicModule = module {
  includes(storageModule)
}

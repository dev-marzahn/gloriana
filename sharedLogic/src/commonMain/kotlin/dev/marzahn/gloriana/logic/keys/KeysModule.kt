package dev.marzahn.gloriana.logic.keys

import org.koin.core.module.Module
import org.koin.dsl.module

internal expect val vaultModule: Module

val keysModule = module {
  includes(vaultModule)
}

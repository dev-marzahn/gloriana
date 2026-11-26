package dev.marzahn.gloriana.android.di

import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.dsl.module

private val androidPlatformModule = module {}

val androidAppModule = module {
  includes(logicModule, uiModule, androidPlatformModule)
}

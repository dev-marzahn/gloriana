package dev.marzahn.gloriana.desktop.di

import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.dsl.module

private val desktopPlatformModule = module {}

val desktopAppModule = module {
  includes(logicModule, uiModule, desktopPlatformModule)
}

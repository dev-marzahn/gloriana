package dev.marzahn.gloriana.desktop.di

import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.storage.DesktopEnvironment
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.dsl.module

private val desktopPlatformModule = module {
  single { desktopGlorianaDatabase(DesktopEnvironment.current().dataDirectory) }
}

val desktopAppModule = module {
  includes(logicModule, uiModule, desktopPlatformModule)
}

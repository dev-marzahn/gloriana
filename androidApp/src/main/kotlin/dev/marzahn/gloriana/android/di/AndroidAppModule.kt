package dev.marzahn.gloriana.android.di

import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.storage.androidGlorianaDatabase
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val androidPlatformModule = module {
  single { androidGlorianaDatabase(androidContext()) }
}

val androidAppModule = module {
  includes(logicModule, uiModule, androidPlatformModule)
}

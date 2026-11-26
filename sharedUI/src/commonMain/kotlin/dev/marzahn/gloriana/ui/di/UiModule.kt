package dev.marzahn.gloriana.ui.di

import dev.marzahn.gloriana.ui.library.LibraryRoute
import dev.marzahn.gloriana.ui.library.LibraryScreen
import dev.marzahn.gloriana.ui.library.LibraryViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val uiModule = module {
  viewModel<LibraryViewModel>()
  navigation<LibraryRoute> { LibraryScreen() }
}

package dev.marzahn.gloriana.ui.personas

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val personasUiModule = module {
  single<PersonaSessions>()
  single<PersonaCommands>()
  viewModel<PersonasViewModel>()
  viewModel<PersonaViewModel>()
  navigation<PersonaRoute.Saved> { route -> PersonaScreen(route) }
  navigation<PersonaRoute.New> { route -> PersonaScreen(route) }
}

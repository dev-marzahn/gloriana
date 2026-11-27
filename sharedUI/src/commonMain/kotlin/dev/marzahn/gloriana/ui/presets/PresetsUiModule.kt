package dev.marzahn.gloriana.ui.presets

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val presetsUiModule = module {
  single<PresetSessions>()
  single<PresetCommands>()
  single<PresetExports>()
  single<PresetImports>()
  viewModel<PresetsViewModel>()
  viewModel<PresetViewModel>()
  navigation<PresetRoute> { route -> PresetScreen(route) }
  navigation<PresetEntryRoute> { route -> PresetEntryScreen(route) }
  navigation<PresetWrapperRoute> { route -> PresetWrapperScreen(route) }
}

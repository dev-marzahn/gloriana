package dev.marzahn.gloriana.ui.navigation

import dev.marzahn.gloriana.ui.presets.PresetRoute
import dev.marzahn.gloriana.ui.presets.PresetScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val presetDestination = module {
  navigation<PresetRoute> { PresetScreen() }
}

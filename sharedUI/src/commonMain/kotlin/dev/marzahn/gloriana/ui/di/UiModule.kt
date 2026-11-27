package dev.marzahn.gloriana.ui.di

import dev.marzahn.gloriana.ui.characters.CardImporter
import dev.marzahn.gloriana.ui.characters.CharacterExports
import dev.marzahn.gloriana.ui.characters.CharacterImports
import dev.marzahn.gloriana.ui.characters.CharacterRoute
import dev.marzahn.gloriana.ui.characters.CharacterScreen
import dev.marzahn.gloriana.ui.characters.CharacterViewModel
import dev.marzahn.gloriana.ui.characters.RepositoryCardImporter
import dev.marzahn.gloriana.ui.chats.ChatsRoute
import dev.marzahn.gloriana.ui.chats.ChatsScreen
import dev.marzahn.gloriana.ui.connections.ConnectionCommands
import dev.marzahn.gloriana.ui.connections.ConnectionRoute
import dev.marzahn.gloriana.ui.connections.ConnectionScreen
import dev.marzahn.gloriana.ui.connections.ConnectionViewModel
import dev.marzahn.gloriana.ui.connections.KeysGateViewModel
import dev.marzahn.gloriana.ui.connections.ModelSettingsRoute
import dev.marzahn.gloriana.ui.connections.ModelSettingsScreen
import dev.marzahn.gloriana.ui.connections.ModelSettingsViewModel
import dev.marzahn.gloriana.ui.connections.PassphraseRoute
import dev.marzahn.gloriana.ui.connections.PassphraseScreen
import dev.marzahn.gloriana.ui.connections.PassphraseViewModel
import dev.marzahn.gloriana.ui.library.LibraryRoute
import dev.marzahn.gloriana.ui.library.LibraryScreen
import dev.marzahn.gloriana.ui.library.LibraryViewModel
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.navigation.LibraryKinds
import dev.marzahn.gloriana.ui.navigation.QuitRequests
import dev.marzahn.gloriana.ui.presets.presetsUiModule
import dev.marzahn.gloriana.ui.settings.SettingsRoute
import dev.marzahn.gloriana.ui.settings.SettingsScreen
import dev.marzahn.gloriana.ui.settings.SettingsViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val uiModule = module {
  includes(presetsUiModule)
  single<AppMessages>()
  single<LibraryKinds>()
  single<QuitRequests>()
  single<ConnectionCommands>()
  single<RepositoryCardImporter>() bind CardImporter::class
  single<CharacterImports>()
  single<CharacterExports>()
  viewModel<LibraryViewModel>()
  viewModel<CharacterViewModel>()
  viewModel<SettingsViewModel>()
  viewModel<ConnectionViewModel>()
  viewModel<ModelSettingsViewModel>()
  viewModel<PassphraseViewModel>()
  viewModel<KeysGateViewModel>()
  navigation<ChatsRoute> { ChatsScreen() }
  navigation<LibraryRoute> { LibraryScreen() }
  navigation<CharacterRoute> { route -> CharacterScreen(route.uuid) }
  navigation<SettingsRoute> { SettingsScreen() }
  navigation<ConnectionRoute.Saved> { route -> ConnectionScreen(route) }
  navigation<ConnectionRoute.New> { route -> ConnectionScreen(route) }
  navigation<ModelSettingsRoute> { ModelSettingsScreen() }
  navigation<PassphraseRoute> { PassphraseScreen() }
}

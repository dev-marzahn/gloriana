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
import dev.marzahn.gloriana.ui.library.LibraryRoute
import dev.marzahn.gloriana.ui.library.LibraryScreen
import dev.marzahn.gloriana.ui.library.LibraryViewModel
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.settings.SettingsRoute
import dev.marzahn.gloriana.ui.settings.SettingsScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val uiModule = module {
  single<AppMessages>()
  single<RepositoryCardImporter>() bind CardImporter::class
  single<CharacterImports>()
  single<CharacterExports>()
  viewModel<LibraryViewModel>()
  viewModel<CharacterViewModel>()
  navigation<ChatsRoute> { ChatsScreen() }
  navigation<LibraryRoute> { LibraryScreen() }
  navigation<CharacterRoute> { route -> CharacterScreen(route.uuid) }
  navigation<SettingsRoute> { SettingsScreen() }
}

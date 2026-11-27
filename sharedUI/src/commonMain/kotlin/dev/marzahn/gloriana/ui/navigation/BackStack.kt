package dev.marzahn.gloriana.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.marzahn.gloriana.ui.characters.CharacterRoute
import dev.marzahn.gloriana.ui.chats.ChatsRoute
import dev.marzahn.gloriana.ui.connections.ConnectionRoute
import dev.marzahn.gloriana.ui.connections.ModelSettingsRoute
import dev.marzahn.gloriana.ui.connections.PassphraseRoute
import dev.marzahn.gloriana.ui.library.LibraryRoute
import dev.marzahn.gloriana.ui.presets.PresetRoute
import dev.marzahn.gloriana.ui.settings.SettingsRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal val backStackConfiguration = SavedStateConfiguration {
  serializersModule = SerializersModule {
    polymorphic(NavKey::class) {
      subclass(ChatsRoute::class)
      subclass(LibraryRoute::class)
      subclass(CharacterRoute::class)
      subclass(SettingsRoute::class)
      subclass(ConnectionRoute.Saved::class)
      subclass(ConnectionRoute.New::class)
      subclass(ModelSettingsRoute::class)
      subclass(PassphraseRoute::class)
      subclass(PresetRoute::class)
    }
  }
}

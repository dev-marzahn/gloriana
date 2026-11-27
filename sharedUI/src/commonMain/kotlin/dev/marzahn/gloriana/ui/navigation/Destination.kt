package dev.marzahn.gloriana.ui.navigation

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.ui.chats.ChatsRoute
import dev.marzahn.gloriana.ui.library.LibraryRoute
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.destination_chats
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.ic_auto_stories
import dev.marzahn.gloriana.ui.resources.ic_auto_stories_filled
import dev.marzahn.gloriana.ui.resources.ic_forum
import dev.marzahn.gloriana.ui.resources.ic_forum_filled
import dev.marzahn.gloriana.ui.resources.ic_settings
import dev.marzahn.gloriana.ui.resources.ic_settings_filled
import dev.marzahn.gloriana.ui.settings.SettingsRoute
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class RailPlacement { Top, Foot }

enum class Destination(
  val root: NavKey,
  val label: StringResource,
  val icon: DrawableResource,
  val selectedIcon: DrawableResource,
  val railPlacement: RailPlacement,
) {
  Chats(
    ChatsRoute,
    Res.string.destination_chats,
    Res.drawable.ic_forum,
    Res.drawable.ic_forum_filled,
    RailPlacement.Top,
  ),
  Library(
    LibraryRoute,
    Res.string.destination_library,
    Res.drawable.ic_auto_stories,
    Res.drawable.ic_auto_stories_filled,
    RailPlacement.Top,
  ),
  Settings(
    SettingsRoute,
    Res.string.destination_settings,
    Res.drawable.ic_settings,
    Res.drawable.ic_settings_filled,
    RailPlacement.Foot,
  ),
  ;

  companion object {
    val Home = Chats
    val Start = Library
  }
}

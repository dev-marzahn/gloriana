package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.marzahn.gloriana.ui.library.LibraryRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal val backStackConfiguration = SavedStateConfiguration {
  serializersModule = SerializersModule {
    polymorphic(NavKey::class) {
      subclass(LibraryRoute::class)
    }
  }
}

@Composable
internal fun rememberGlorianaBackStack(): NavBackStack<NavKey> =
  rememberNavBackStack(backStackConfiguration, LibraryRoute)

internal fun MutableList<NavKey>.popAboveRoot() {
  if (size > 1) removeAt(lastIndex)
}

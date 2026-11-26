package dev.marzahn.gloriana.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import dev.marzahn.gloriana.ui.library.LibraryRoute
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackTest {
  private data object Above : NavKey

  @Test
  fun backPopsTheTopRoute() {
    val backStack = mutableListOf<NavKey>(LibraryRoute, Above)
    backStack.popAboveRoot()
    assertEquals(listOf<NavKey>(LibraryRoute), backStack)
  }

  @Test
  fun backNeverRemovesTheRoot() {
    val backStack = mutableListOf<NavKey>(LibraryRoute)
    backStack.popAboveRoot()
    assertEquals(listOf<NavKey>(LibraryRoute), backStack)
  }

  @Test
  fun libraryRouteSurvivesSavedState() {
    val serializer = PolymorphicSerializer(NavKey::class)
    val saved = encodeToSavedState(serializer, LibraryRoute, backStackConfiguration)
    assertEquals(LibraryRoute, decodeFromSavedState(serializer, saved, backStackConfiguration))
  }
}

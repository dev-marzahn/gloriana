package dev.marzahn.gloriana.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackTest {
  @Test
  fun everyDestinationsRootSurvivesSavedState() {
    val serializer = PolymorphicSerializer(NavKey::class)
    Destination.entries.forEach { destination ->
      val saved = encodeToSavedState(serializer, destination.root, backStackConfiguration)
      assertEquals(destination.root, decodeFromSavedState(serializer, saved, backStackConfiguration))
    }
  }
}

package dev.marzahn.gloriana.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.characters.CharacterRoute
import dev.marzahn.gloriana.ui.connections.ConnectionRoute
import dev.marzahn.gloriana.ui.connections.ModelSettingsRoute
import dev.marzahn.gloriana.ui.connections.NewConnectionStart
import dev.marzahn.gloriana.ui.connections.Opening
import dev.marzahn.gloriana.ui.connections.PassphraseRoute
import dev.marzahn.gloriana.ui.presets.PresetEntryRoute
import dev.marzahn.gloriana.ui.presets.PresetRoute
import dev.marzahn.gloriana.ui.presets.PresetWrapperRoute
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class BackStackTest {
  private val serializer = PolymorphicSerializer(NavKey::class)

  private fun assertSurvives(key: NavKey) {
    val saved = encodeToSavedState(serializer, key, backStackConfiguration)
    assertEquals(key, decodeFromSavedState(serializer, saved, backStackConfiguration))
  }

  @Test
  fun everyDestinationsRootSurvivesSavedState() {
    Destination.entries.forEach { assertSurvives(it.root) }
  }

  @Test
  fun everyPushedScreenSurvivesSavedState() {
    val opening = Opening.fresh()
    listOf(
      CharacterRoute(Uuid.random().toString()),
      ConnectionRoute.Saved(Uuid.random()),
      ConnectionRoute.New(NewConnectionStart.FromNothing, opening),
      ModelSettingsRoute,
      PassphraseRoute,
      PresetRoute(Uuid.random().toString()),
      PresetEntryRoute(Uuid.random(), Uuid.random()),
    ).forEach(::assertSurvives)
    WrapperPrompt.entries.forEach { assertSurvives(PresetWrapperRoute(Uuid.random(), it)) }
    NewConnectionStart.choices.forEach { assertSurvives(ConnectionRoute.New(it, opening)) }
  }
}

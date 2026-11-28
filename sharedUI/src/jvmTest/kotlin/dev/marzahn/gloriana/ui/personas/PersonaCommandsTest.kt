package dev.marzahn.gloriana.ui.personas

import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.ui.components.NotDeleted
import dev.marzahn.gloriana.ui.lists.ItemActivity
import dev.marzahn.gloriana.ui.lists.ItemWork
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.navigation.Opening
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

class PersonaCommandsTest {
  private class Fixture(val library: LiveLibrary) {
    val sessions: PersonaSessions = library.koin.get()
    val commands: PersonaCommands = library.koin.get()
    val route = PersonaRoute.New(Opening.fresh(UuidV7IdFactory))

    fun named(name: String) = sessions.edit(route, PersonaIntent.SetName(name))

    fun awaitSession(route: PersonaRoute, reached: (PersonaSession) -> Boolean) = runBlocking {
      withTimeout(WAIT_MILLIS) { sessions.session(route).first(reached) }
    }

    fun awaitSavedMessage(): String = runBlocking {
      withTimeout(WAIT_MILLIS) { library.koin.get<AppMessages>().messages.first().text }
    }
  }

  private fun withNewPersona(block: Fixture.() -> Unit) = LiveLibrary.use { Fixture(it).block() }

  @Test
  fun aSecondSaveWhileTheFirstIsWritingCreatesNothingMore() = withNewPersona {
    named("Wren")
    library.writes.hold()

    assertNull(commands.save(route))
    assertTrue(sessions.current(route).isSaving)
    assertNull(commands.save(route))
    library.writes.release()

    awaitSavedMessage()
    assertEquals(1, library.personas().count { it.fields.name == "Wren" })
  }

  @Test
  fun aNewPersonaIsStoredUnderItsOpeningsIdSoItsAvatarKeepsItsColour() = withNewPersona {
    named("Wren")
    commands.save(route)

    awaitSavedMessage()
    assertEquals(route.opening.id, library.personas().single { it.fields.name == "Wren" }.id)
    assertEquals(route.avatarId, PersonaRoute.Saved(route.opening.id).avatarId)
  }

  @Test
  fun aNewPersonaLeftWhileSavingOffersNoDiscardAndIsStillSaved() = withNewPersona {
    named("Wren")
    library.writes.hold()
    commands.save(route)

    assertFalse(sessions.current(route).hasChanges)
    sessions.closed(route)
    library.writes.release()
    awaitSavedMessage()

    val left = sessions.current(route)
    assertTrue(left.holdsNothing(route))
    assertNull(left.savedAs)
    assertTrue(library.personas().any { it.fields.name == "Wren" })
  }

  @Test
  fun aPersonaBeingEditedIsNeitherMadeTheDefaultNorAskedAboutDeleting() = withNewPersona {
    val wren = library.createdPersona("Wren")
    val saved = PersonaRoute.Saved(wren.id)
    sessions.startEditing(saved, wren.fields)

    commands.onStored.makeDefault(wren.id, wren.fields.name)
    commands.onStored.askToDelete(wren.id)

    assertEquals(ItemActivity.Idle, sessions.current(saved).work.activity)
    assertTrue(library.defaultPersona().id != wren.id)
  }

  @Test
  fun editingAPersonaWhoseDeleteWasRefusedClearsTheNoteItCouldNotAct() = withNewPersona {
    val default = library.defaultPersona()
    val saved = PersonaRoute.Saved(default.id)
    commands.onStored.askToDelete(default.id)
    awaitSession(saved) { it.work.activity is ItemActivity.ConfirmingDelete }
    commands.onStored.confirmDelete(default.id, default.fields.name)
    awaitSession(saved) { it.work.notDeleted == NotDeleted.IsDefault }

    sessions.startEditing(saved, default.fields)

    assertEquals(ItemWork(), sessions.current(saved).work)
  }

  @Test
  fun aBlankNameIsRefusedAndMarkedMissing() = withNewPersona {
    named("   ")

    assertEquals(PersonaSaveRefusal.NoName, commands.save(route))
    assertTrue(sessions.current(route).nameMissing)
  }
}

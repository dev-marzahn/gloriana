package dev.marzahn.gloriana.ui.personas

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.Opening
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.replaceTop
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed interface PersonaRoute : NavKey {
  val avatarId: Uuid

  @Serializable
  data class Saved(val id: Uuid) : PersonaRoute {
    override val avatarId: Uuid get() = id
  }

  @Serializable
  data class New(val opening: Opening) : PersonaRoute {
    override val avatarId: Uuid get() = opening.id
  }
}

fun ShellNavigator.showPersonas() = showLibraryKind(LibraryKind.Personas)

fun ShellNavigator.pickPersona(id: Uuid) {
  replaceTop(Destination.Library, PersonaRoute.Saved(id))
}

fun ShellNavigator.createPersona(ids: IdFactory) {
  replaceTop(Destination.Library, PersonaRoute.New(Opening.fresh(ids)))
}

fun ShellNavigator.shownPersona(): Uuid? =
  stack(Destination.Library).filterIsInstance<PersonaRoute.Saved>().firstOrNull()?.id

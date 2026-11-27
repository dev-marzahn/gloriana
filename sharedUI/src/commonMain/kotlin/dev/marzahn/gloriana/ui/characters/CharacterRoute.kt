package dev.marzahn.gloriana.ui.characters

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.replaceTop
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CharacterRoute(val id: String) : NavKey {
  constructor(id: Uuid) : this(id.toString())

  val uuid: Uuid get() = Uuid.parse(id)
}

fun ShellNavigator.showLibrary() = showLibraryKind(LibraryKind.Characters)

fun ShellNavigator.openCharacter(id: Uuid) {
  showLibraryKind(LibraryKind.Characters) { push(CharacterRoute(id), Destination.Library) }
}

fun ShellNavigator.pickCharacter(id: Uuid) {
  replaceTop(Destination.Library, CharacterRoute(id))
}

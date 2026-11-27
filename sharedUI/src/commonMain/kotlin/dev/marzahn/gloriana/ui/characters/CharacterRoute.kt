package dev.marzahn.gloriana.ui.characters

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CharacterRoute(val id: String) : NavKey {
  constructor(id: Uuid) : this(id.toString())

  val uuid: Uuid get() = Uuid.parse(id)
}

fun ShellNavigator.showLibrary() {
  if (current != Destination.Library) select(Destination.Library)
  while (!atRoot) pop(Destination.Library)
}

fun ShellNavigator.openCharacter(id: Uuid) {
  showLibrary()
  push(CharacterRoute(id))
}

fun ShellNavigator.pickCharacter(id: Uuid) {
  while (!atRoot) pop(current)
  push(CharacterRoute(id))
}

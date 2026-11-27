package dev.marzahn.gloriana.ui.presets

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.LibraryKinds
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.replaceTop
import dev.marzahn.gloriana.ui.navigation.showLibraryKind
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

sealed interface PresetPart : NavKey {
  val presetId: String

  val presetUuid: Uuid get() = Uuid.parse(presetId)
}

@Serializable
data class PresetRoute(override val presetId: String) : PresetPart {
  constructor(id: Uuid) : this(id.toString())
}

@Serializable
data class PresetEntryRoute(override val presetId: String, val entryId: String) : PresetPart {
  constructor(presetId: Uuid, entryId: Uuid) : this(presetId.toString(), entryId.toString())

  val entryUuid: Uuid get() = Uuid.parse(entryId)
}

@Serializable
data class PresetWrapperRoute(override val presetId: String, val prompt: WrapperPrompt) : PresetPart {
  constructor(presetId: Uuid, prompt: WrapperPrompt) : this(presetId.toString(), prompt)
}

fun ShellNavigator.openPreset(id: Uuid, kinds: LibraryKinds) {
  showLibraryKind(LibraryKind.Presets, kinds) { push(PresetRoute(id)) }
}

fun ShellNavigator.pickPreset(id: Uuid) {
  replaceTop(Destination.Library, PresetRoute(id))
}

fun ShellNavigator.shownPreset(): Uuid? =
  stack(Destination.Library).filterIsInstance<PresetPart>().firstOrNull()?.presetUuid

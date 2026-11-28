package dev.marzahn.gloriana.ui.presets

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.replaceTop
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

sealed interface PresetPart : NavKey {
  val presetId: Uuid
}

@Serializable
data class PresetRoute(override val presetId: Uuid) : PresetPart

@Serializable
data class PresetEntryRoute(override val presetId: Uuid, val entryId: Uuid) : PresetPart

@Serializable
data class PresetWrapperRoute(override val presetId: Uuid, val prompt: WrapperPrompt) : PresetPart

fun ShellNavigator.showPresets() = showLibraryKind(LibraryKind.Presets)

fun ShellNavigator.openPreset(id: Uuid) {
  showLibraryKind(LibraryKind.Presets) { push(PresetRoute(id), Destination.Library) }
}

fun ShellNavigator.pickPreset(id: Uuid) {
  replaceTop(Destination.Library, PresetRoute(id))
}

fun ShellNavigator.shownPreset(): Uuid? =
  stack(Destination.Library).filterIsInstance<PresetPart>().firstOrNull()?.presetId

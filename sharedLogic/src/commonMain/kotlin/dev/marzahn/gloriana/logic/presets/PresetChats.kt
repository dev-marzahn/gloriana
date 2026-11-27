package dev.marzahn.gloriana.logic.presets

import kotlin.uuid.Uuid

fun interface PresetChats {
  suspend fun count(presetId: Uuid): Int

  companion object {
    val NoneYet = PresetChats { 0 }
  }
}

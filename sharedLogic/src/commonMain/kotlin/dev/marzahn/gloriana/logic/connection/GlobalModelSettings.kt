package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.connection.storage.GlobalSamplersSetting
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface GlobalSamplersSave {
  data object Saved : GlobalSamplersSave

  data class Refused(val notFinite: Set<DecimalSampler>) : GlobalSamplersSave

  data object Failed : GlobalSamplersSave
}

class GlobalModelSettings(private val settings: LibrarySettings) {
  fun samplers(): Flow<Samplers> = settings.observe(GlobalSamplersSetting).map { it ?: Samplers() }

  suspend fun setSamplers(samplers: Samplers): GlobalSamplersSave {
    val notFinite = samplers.notFinite()
    if (notFinite.isNotEmpty()) return GlobalSamplersSave.Refused(notFinite)
    return failingAs(GlobalSamplersSave.Failed) {
      settings.write(GlobalSamplersSetting, samplers)
      GlobalSamplersSave.Saved
    }
  }
}

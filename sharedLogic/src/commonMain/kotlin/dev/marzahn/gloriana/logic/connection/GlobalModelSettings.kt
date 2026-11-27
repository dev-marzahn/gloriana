package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.connection.storage.GlobalSamplersSetting
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface GlobalSamplersSave {
  data object Saved : GlobalSamplersSave

  data class Refused(val outsideEveryFormat: Set<Sampler>) : GlobalSamplersSave

  data object StorageFull : GlobalSamplersSave

  data object Failed : GlobalSamplersSave
}

class GlobalModelSettings(private val settings: LibrarySettings) {
  fun samplers(): Flow<Samplers> = settings.observe(GlobalSamplersSetting).map { it ?: Samplers() }

  suspend fun currentSamplers(): Samplers = settings.read(GlobalSamplersSetting) ?: Samplers()

  suspend fun setSamplers(samplers: Samplers): GlobalSamplersSave {
    val outside = samplers.outsideEveryFormat()
    if (outside.isNotEmpty()) return GlobalSamplersSave.Refused(outside)
    return failingAs({ if (it.isStorageFull()) GlobalSamplersSave.StorageFull else GlobalSamplersSave.Failed }) {
      settings.write(GlobalSamplersSetting, samplers)
      GlobalSamplersSave.Saved
    }
  }
}

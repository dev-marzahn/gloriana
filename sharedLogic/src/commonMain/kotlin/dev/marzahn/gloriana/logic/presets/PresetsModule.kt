package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.presets.storage.PresetWrites
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import org.koin.dsl.module

val presetsModule = module {
  single { get<GlorianaDatabase>().presetDao() }
  single { PresetChats.NoneYet }
  single { PresetWrites(get(), get(), get(), get(), get()) }
  single { PresetRepository(get(), get(), get(), get()) }
  single { PresetImport(get(), get()) }
  single { SamplerOffer(get(), get()) }
}

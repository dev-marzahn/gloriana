package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetFiles
import dev.marzahn.gloriana.logic.assets.AssetPurge
import dev.marzahn.gloriana.logic.assets.AssetPurgeAtStart
import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.characters.files.JvmCardReader
import dev.marzahn.gloriana.logic.characters.files.JvmCardWriter
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val cardFilesModule: Module = module {
  single { AssetStore(get()) } bind AssetFiles::class
  single { JvmCardReader(get()) } bind CardReader::class
  single { JvmCardWriter(get(), get()) } bind CardWriter::class
  single { DebugHooks(get(), get()) }
  single { get<DebugHooks>().faults }
  single { get<DebugHooks>().importHold }
  single { get<DebugHooks>().purge }
  single { AssetPurge(get(), get(), get(), get(), get()) }
  single(createdAtStart = true) { AssetPurgeAtStart(get(), get(), get()) }
}

package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.assets.AssetReferences
import dev.marzahn.gloriana.logic.assets.AssetWriteLock
import dev.marzahn.gloriana.logic.characters.storage.CharacterDao
import dev.marzahn.gloriana.logic.characters.storage.CharacterWrites
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal expect val cardFilesModule: Module

val charactersModule = module {
  includes(cardFilesModule)
  single { get<GlorianaDatabase>().characterDao() }
  single { AssetWriteLock() }
  single { CharacterAssetReferences(get()) } bind AssetReferences::class
  single { CharacterWrites(get(), get(), get(), get(), get(), get()) }
  single { CharacterRepository(get(), get(), get(), get(), get(), get(), get()) }
  single { CharacterLookup(get()) }
}

internal class CharacterAssetReferences(private val dao: CharacterDao) : AssetReferences {
  override suspend fun referencedHashes(): Set<AssetHash> = dao.storedHashes().map(::AssetHash).toSet()
}

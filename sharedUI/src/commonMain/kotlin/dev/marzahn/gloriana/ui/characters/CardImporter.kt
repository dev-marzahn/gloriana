package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryQuestion
import dev.marzahn.gloriana.logic.characters.CharacterRepository
import dev.marzahn.gloriana.logic.characters.FileImportResult
import dev.marzahn.gloriana.logic.characters.ImportStatusListener
import dev.marzahn.gloriana.logic.characters.PickedFile
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

interface CardImporter {
  suspend fun import(
    files: List<PickedFile>,
    alreadyInLibrary: AlreadyInLibraryQuestion,
    listener: ImportStatusListener,
  ): List<FileImportResult>

  suspend fun nameOf(id: Uuid): String?
}

class RepositoryCardImporter(private val repository: CharacterRepository) : CardImporter {
  override suspend fun import(
    files: List<PickedFile>,
    alreadyInLibrary: AlreadyInLibraryQuestion,
    listener: ImportStatusListener,
  ): List<FileImportResult> = repository.import(files, alreadyInLibrary, listener)

  override suspend fun nameOf(id: Uuid): String? = repository.character(id).first()?.fields?.name
}

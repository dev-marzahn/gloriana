package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.AssetWriteLock
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.characters.storage.CharacterDao
import dev.marzahn.gloriana.logic.characters.storage.CharacterWrite
import dev.marzahn.gloriana.logic.characters.storage.CharacterWrites
import dev.marzahn.gloriana.logic.characters.storage.matchKey
import dev.marzahn.gloriana.logic.characters.storage.toCharacter
import dev.marzahn.gloriana.logic.characters.storage.toSummary
import dev.marzahn.gloriana.logic.characters.storage.verbatimCard
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.storage.ChatsWithCharacter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class CharacterRepository internal constructor(
  private val dao: CharacterDao,
  private val writes: CharacterWrites,
  private val reader: CardReader,
  private val writer: CardWriter,
  private val hold: ImportHold,
  private val assetLock: AssetWriteLock,
  private val chats: ChatsWithCharacter,
) {
  fun characters(): Flow<List<CharacterSummary>> = dao.summaries().map { rows -> rows.map { it.toSummary() } }

  fun character(id: Uuid): Flow<Character?> = dao.observeRows(id).map { it?.toCharacter() }

  suspend fun import(
    files: List<PickedFile>,
    alreadyInLibrary: AlreadyInLibraryQuestion,
    listener: ImportStatusListener = ImportStatusListener { _, _ -> },
  ): List<FileImportResult> = assetLock.whileImporting {
    files.indices.forEach { listener.onStatus(it, FileImportStatus.Waiting) }
    val answers = PickAnswers(alreadyInLibrary)
    files.mapIndexed { index, file ->
      listener.onStatus(index, FileImportStatus.Importing)
      val result = importOne(file, filesLeft = files.size - index - 1, answers)
      listener.onStatus(index, FileImportStatus.Done(result))
      result
    }
  }

  suspend fun export(id: Uuid, format: CardFormat, destination: ExportDestination): ExportResult {
    val rows = dao.rows(id) ?: return ExportResult.CharacterGone
    val card = CardForExport(rows.toCharacter(), rows.verbatimCard())
    return withContext(Dispatchers.IO) { writer.write(card, format, destination) }
  }

  suspend fun chatsWith(id: Uuid): Int = chats.count(id)

  suspend fun delete(id: Uuid, chatsOfIt: ChatsOfDeletedCharacter): DeleteResult = failingAs(DeleteResult.Failed) {
    if (writes.delete(id, chatsOfIt)) DeleteResult.Deleted else DeleteResult.Gone
  }

  private suspend fun importOne(file: PickedFile, filesLeft: Int, answers: PickAnswers): FileImportResult {
    hold.awaitRelease(file.name)
    return when (val read = readClosingOnCancel(file)) {
      is CardFileRead.Failed -> FileImportResult.Failed(read.reason)
      is CardFileRead.Card -> read.card.use { card -> write(card, decide(card, file.name, filesLeft, answers)) }
    }
  }

  private suspend fun readClosingOnCancel(file: PickedFile): CardFileRead {
    var read: CardFileRead? = null
    try {
      return withContext(Dispatchers.IO) { reader.read(file).also { read = it } }
    } catch (cancelled: CancellationException) {
      (read as? CardFileRead.Card)?.card?.close()
      throw cancelled
    }
  }

  private suspend fun decide(
    card: ImportedCard,
    fileName: String,
    filesLeft: Int,
    answers: PickAnswers,
  ): ImportDecision {
    val fields = card.parsed.fields
    val match = dao.lastImportedMatch(matchKey(fields.name), matchKey(fields.creator))?.toSummary()
      ?: return ImportDecision.New
    val losesLorebook = dao.rows(match.id)?.character?.keptFields?.hasEmbeddedLorebook == true &&
      !card.parsed.kept.hasEmbeddedLorebook
    val question = AlreadyInLibrary(
      fileName,
      match,
      filesLeft,
      losesEmbeddedLorebook = losesLorebook,
      fileHoldsNoFiles = card is ImportedCard.JsonOnly,
    )
    return when (answers.choiceFor(question)) {
      AlreadyInLibraryChoice.Copy -> ImportDecision.New
      AlreadyInLibraryChoice.Update -> ImportDecision.UpdateMatch
      AlreadyInLibraryChoice.Cancel -> ImportDecision.LeaveOut
    }
  }

  private suspend fun write(card: ImportedCard, decision: ImportDecision): FileImportResult = when (decision) {
    ImportDecision.LeaveOut -> FileImportResult.LeftOut
    ImportDecision.New -> saving(card) { FileImportResult.Imported(writes.insert(card.newCharacter())) }
    ImportDecision.UpdateMatch -> saving(card) {
      when (val written = writes.replaceMatchOrInsert(card.parsed.fields, card::replacing, card::newCharacter)) {
        is CharacterWrite.Inserted -> FileImportResult.Imported(written.id)
        is CharacterWrite.Replaced -> FileImportResult.Updated(written.id)
      }
    }
  }

  private suspend fun saving(card: ImportedCard, write: suspend () -> FileImportResult): FileImportResult =
    failingAs({ error ->
      FileImportResult.Failed(if (error.isStorageFull()) ImportFailure.StorageFull else ImportFailure.CouldNotSave)
    }) {
      withContext(Dispatchers.IO) { card.commit() }
      write()
    }
}

internal sealed interface ImportDecision {
  data object New : ImportDecision

  data object UpdateMatch : ImportDecision

  data object LeaveOut : ImportDecision
}

private class PickAnswers(private val question: AlreadyInLibraryQuestion) {
  private var standing: AlreadyInLibraryChoice? = null

  suspend fun choiceFor(alreadyInLibrary: AlreadyInLibrary): AlreadyInLibraryChoice =
    standing ?: question.ask(alreadyInLibrary).let { answer ->
      if (answer.forTheRestOfThePick) standing = answer.choice
      answer.choice
    }
}

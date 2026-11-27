package dev.marzahn.gloriana.logic.characters

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class FaultPointTest {
  @TempDir
  lateinit var directory: Path

  @TestFactory
  fun aWriteThatFailsAtAnyFaultPointLeavesTheLibraryAsItWas() = CharacterWritePoint.entries.map { point ->
    DynamicTest.dynamicTest(point.name) {
      var armed = false
      val faults = CharacterWriteFaults { reached -> if (armed && reached == point) throw FaultInjected(reached) }
      CharacterLibrary(Files.createTempDirectory(directory, point.name), faults = faults).use { library ->
        runTest {
          val tamsin = library.importedCharacter(ImporterCorpus.charx)
          val before = library.list() to library.database.tombstoneDao().all()
          armed = true

          val outcome = when (point) {
            CharacterWritePoint.InsertRowWritten -> library.importOne(ImporterCorpus.ccv1Json)
            CharacterWritePoint.UpdateChildrenRemoved, CharacterWritePoint.UpdateRowWritten ->
              library.importOne(ImporterCorpus.charxUpdated, AlreadyInLibraryChoice.Update)
            CharacterWritePoint.DeleteChildrenRemoved, CharacterWritePoint.DeleteRowRemoved ->
              library.repository.delete(tamsin.id)
          }

          val expected: Any = if (point.name.startsWith("Delete")) {
            DeleteResult.Failed
          } else {
            FileImportResult.Failed(ImportFailure.CouldNotSave)
          }
          assertEquals(expected, outcome)
          assertEquals(before, library.list() to library.database.tombstoneDao().all())
          assertEquals(tamsin, library.character(tamsin.id))
        }
      }
    }
  }
}

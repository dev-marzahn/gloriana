package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetSwitch
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.DebugHooks
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugHooksTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aDebuggableBuildFailsTheWriteAtTheConfiguredFaultPoint() = runTest {
    writeHooks("fault.characters.DeleteRowRemoved=fail")
    val hooks = DebugHooks(LibraryDirectory { directory.resolve("library") }, { true })
    CharacterLibrary(directory.resolve("library"), faults = hooks).use { library ->
      val tamsin = library.importedCharacter(ImporterCorpus.charx)

      assertEquals(DeleteResult.Failed, library.repository.delete(tamsin.id, ChatsOfDeletedCharacter.Keep))
      assertEquals(tamsin, library.character(tamsin.id))
    }
  }

  @Test
  fun aReleaseBuildIgnoresTheHooksFile() = runTest {
    writeHooks("fault.characters.DeleteRowRemoved=fail", "assets.purge.now=on")
    val debugHooks = DebugHooks(LibraryDirectory { directory.resolve("library") }, { false })
    CharacterLibrary(directory.resolve("library"), faults = debugHooks).use { library ->
      val tamsin = library.importedCharacter(ImporterCorpus.charx)

      assertEquals(DeleteResult.Deleted, library.repository.delete(tamsin.id, ChatsOfDeletedCharacter.Keep))
      assertFalse(debugHooks.isOn(AssetSwitch.PurgeNow))
    }
  }

  @Test
  fun aHeldFileShowsAsImportingUntilItIsReleased() = runTest {
    writeHooks("characters.import.hold=on")
    val hold = DebugImportHold(DebugHooks(LibraryDirectory { directory.resolve("library") }, { true }))
    CharacterLibrary(directory.resolve("library"), hold = hold).use { library ->
      val statuses = mutableListOf<FileImportStatus>()
      val pick = async {
        library.repository.import(listOf(ImporterCorpus.ccv1Json), { error("asked") }) { _, status ->
          statuses += status
        }
      }
      while (FileImportStatus.Importing !in statuses) kotlinx.coroutines.yield()

      assertEquals(emptyList(), library.list())
      Files.createFile(directory.resolve("library").resolve("debug").resolve("release-import"))
      val result = pick.await().single()

      assertTrue(result is FileImportResult.Imported)
    }
  }

  private fun writeHooks(vararg lines: String) {
    val hooks = directory.resolve("library").resolve("debug")
    Files.createDirectories(hooks)
    Files.write(hooks.resolve("hooks.properties"), lines.joinToString("\n").toByteArray())
  }
}

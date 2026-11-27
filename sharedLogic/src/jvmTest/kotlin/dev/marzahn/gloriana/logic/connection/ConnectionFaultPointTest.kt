package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.keys.DataKeysSweptAtStart
import dev.marzahn.gloriana.logic.keys.KeyWritePoint
import dev.marzahn.gloriana.logic.keys.Vaults
import dev.marzahn.gloriana.logic.keys.apiKey
import dev.marzahn.gloriana.logic.keys.opened
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConnectionFaultPointTest {
  @TempDir
  lateinit var directory: Path

  private val newId = Uuid.random()
  private var failAt: FaultPoint? = null
  private val failing = Faults { if (it == failAt) throw FaultInjected(it) }

  private fun library() = ConnectionLibrary(directory, faults = failing, keyFaults = failing, ids = { newId })

  @Test
  fun aNewConnectionWhoseSaveFailsLeavesNoRowNoDefaultAndNoDataKey() = runTest {
    library().use { library ->
      failAt = ConnectionWritePoint.InsertRowWritten

      val result = library.repository.create(
        draft(key = KeyInput.Entered("sk-failed"), headers = listOf(HeaderDraft("X-Org", entered("org-7")))),
      )

      assertEquals(ConnectionSaveResult.Failed, result)
      assertEquals(emptyList(), library.list())
      assertNull(library.default())
      assertTrue(Files.exists(library.dataKeyFile()), "the save made a data key first")
      assertFalse(library.dataKeyFileHolds(newId))
    }
  }

  @Test
  fun aDataKeyThatCannotBeWrittenFailsTheSaveAndStoresNothing() = runTest {
    library().use { library ->
      library.created(draft(name = "First", key = KeyInput.Entered("sk-first")))
      val keys = library.dataKeyFile().parent
      Files.setPosixFilePermissions(keys, PosixFilePermissions.fromString("r-x------"))
      val result = try {
        library.repository.create(draft(name = "Second", key = KeyInput.Entered("sk-second")))
      } finally {
        Files.setPosixFilePermissions(keys, PosixFilePermissions.fromString("rwx------"))
      }

      assertEquals(ConnectionSaveResult.Failed, result)
      assertEquals(listOf("First"), library.list().map { it.fields.name })
    }
  }

  @Test
  fun aFailedEditKeepsTheConnectionAndTheDataKeyItAlreadyHad() = runTest {
    ConnectionLibrary(directory, faults = failing).use { library ->
      val stored = library.created(draft(key = KeyInput.Entered("sk-kept")))
      failAt = ConnectionWritePoint.UpdateRowWritten

      val result = library.repository.update(stored.id, draft(name = "Renamed", key = KeyInput.Entered("sk-new")))

      assertEquals(ConnectionSaveResult.Failed, result)
      val unchanged = library.stored(stored.id)
      assertEquals(stored.fields, unchanged.fields)
      assertEquals("sk-kept", library.vault.opened(apiKey(stored.id), checkNotNull(unchanged.key)))
    }
  }

  @Test
  fun aFailedEditOfAConnectionWithNoSecretsLeavesNoDataKey() = runTest {
    ConnectionLibrary(directory, faults = failing).use { library ->
      val stored = library.created(draft())
      failAt = ConnectionWritePoint.UpdateRowWritten

      val result = library.repository.update(stored.id, draft(key = KeyInput.Entered("sk-new")))

      assertEquals(ConnectionSaveResult.Failed, result)
      assertNull(library.stored(stored.id).key)
      assertFalse(library.dataKeyFileHolds(stored.id))
    }
  }

  @Test
  fun aDeleteThatFailsLeavesTheConnectionTheDefaultAndItsKey() = runTest {
    ConnectionLibrary(directory, faults = failing).use { library ->
      val stored = library.created(draft(key = KeyInput.Entered("sk-stays")))
      failAt = ConnectionWritePoint.DeleteRowRemoved

      assertEquals(ConnectionDeleteResult.Failed, library.repository.delete(stored.id))

      assertEquals(listOf(stored.id), library.list().map { it.id })
      assertEquals(stored.id, library.default())
      assertEquals(emptyList(), library.database.tombstoneDao().all())
      assertEquals("sk-stays", library.vault.opened(apiKey(stored.id), checkNotNull(stored.key)))
    }
  }

  @Test
  fun anEraseThatFailsAfterTheDeleteIsReportedAndFinishedByTheNextStartsSweep() = runTest {
    val vaults = Vaults(directory)
    val deleted = ConnectionLibrary(directory, vaults = vaults, keyFaults = failing).use { library ->
      val deleted = library.created(draft(name = "Deleted", key = KeyInput.Entered("sk-deleted")))
      library.created(draft(name = "Kept", key = KeyInput.Entered("sk-kept")))
      failAt = KeyWritePoint.BeforeDataKeyRemoved

      assertEquals(ConnectionDeleteResult.DeletedKeyLeftForNextStart, library.repository.delete(deleted.id))
      assertTrue(library.dataKeyFileHolds(deleted.id))
      deleted
    }

    ConnectionLibrary(directory, vaults = vaults).use { restarted ->
      val start = ApplicationScope { throw it }
      DataKeysSweptAtStart(restarted.vault, start)
      start.coroutineContext.job.children.toList().joinAll()
      start.cancel()

      assertFalse(restarted.dataKeyFileHolds(deleted.id))
      val kept = restarted.list().single()
      assertEquals("sk-kept", restarted.vault.opened(apiKey(kept.id), checkNotNull(kept.key)))
    }
  }
}

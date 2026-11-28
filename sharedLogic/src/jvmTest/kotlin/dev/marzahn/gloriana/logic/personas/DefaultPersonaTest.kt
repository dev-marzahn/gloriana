package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.storage.DefaultPersonaSetting
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class DefaultPersonaTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aFreshLibraryGetsTheShippedPersonaAsItsDefaultOnce() = runTest {
    PersonaLibrary(directory).use { library ->
      library.default.ensureShipped()
      library.default.ensureShipped()

      val personas = library.repository.personas().first()
      assertEquals(listOf(PersonaFields(name = "User")), personas.map { it.fields })
      assertEquals(personas.single().id, library.defaultId())
    }
  }

  @Test
  fun aLibraryWhoseDefaultIsSetAndStoredGetsNoShippedPersona() = runTest {
    PersonaLibrary(directory).use { library ->
      val own = library.created(wren())
      assertEquals(MakeDefaultResult.Made, library.repository.makeDefault(own.id))

      library.default.ensureShipped()

      assertEquals(listOf(own), library.repository.personas().first())
    }
  }

  @Test
  fun aDefaultSettingNamingNoStoredPersonaCountsAsUnsetAndGetsTheShippedOne() = runTest {
    PersonaLibrary(directory).use { library ->
      val own = library.created(wren())
      library.settings.write(DefaultPersonaSetting, Uuid.random())
      assertNull(library.defaultId())

      library.default.ensureShipped()

      val shipped = library.repository.personas().first().single { it.id != own.id }
      assertEquals("User", shipped.fields.name)
      assertEquals(shipped.id, library.defaultId())
    }
  }

  @Test
  fun theDefaultCantBeDeletedUntilAnotherIsTheDefault() = runTest {
    PersonaLibrary(directory).use { library ->
      library.default.ensureShipped()
      val shipped = checkNotNull(library.defaultId())
      val other = library.created(wren())

      assertEquals(PersonaDeleteResult.IsDefault, library.repository.delete(shipped))
      assertEquals(shipped, library.stored(shipped).id)
      assertEquals(emptyList(), library.database.tombstoneDao().all())

      assertEquals(MakeDefaultResult.Made, library.repository.makeDefault(other.id))
      assertEquals(other.id, library.defaultId())
      assertEquals(PersonaDeleteResult.Deleted, library.repository.delete(shipped))
      assertEquals(PersonaDeleteResult.IsDefault, library.repository.delete(other.id))
    }
  }

  @Test
  fun makingAPersonaNoLongerStoredTheDefaultIsGoneAndChangesNothing() = runTest {
    PersonaLibrary(directory).use { library ->
      library.default.ensureShipped()
      val shipped = library.defaultId()

      assertEquals(MakeDefaultResult.Gone, library.repository.makeDefault(Uuid.random()))

      assertEquals(shipped, library.defaultId())
    }
  }

  @Test
  fun aShippedPersonaWriteThatFailsPartWayLeavesNoRowAndNoDefault() = runTest {
    val failing = Faults { point -> if (point == PersonaWritePoint.InsertRowWritten) throw FaultInjected(point) }
    PersonaLibrary(directory, faults = failing).use { library ->
      assertThrows<FaultInjected> { library.default.ensureShipped() }

      assertEquals(emptyList(), library.repository.personas().first())
      assertNull(library.settings.read(DefaultPersonaSetting))
    }
  }
}

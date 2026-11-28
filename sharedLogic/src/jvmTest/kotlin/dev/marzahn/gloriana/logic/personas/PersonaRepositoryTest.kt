package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.storage.PersonasEntityType
import dev.marzahn.gloriana.logic.storage.RowKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class PersonaRepositoryTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aCreatedPersonaReadsBackWithEveryFieldAndATrimmedName() = runTest {
    PersonaLibrary(directory).use { library ->
      val fields = wren().copy(
        name = "  Wren ",
        pronouns = Pronouns("she", "her", "her", "hers", "herself"),
      )

      val persona = library.created(fields)

      assertEquals(fields.copy(name = "Wren"), persona.fields)
    }
  }

  @Test
  fun aPromptReadsOnlyTheNameDescriptionAndPronouns() = runTest {
    PersonaLibrary(directory).use { library ->
      val pronouns = Pronouns("she", "her", "her", "hers", "herself")
      val persona = library.created(wren().copy(pronouns = pronouns))

      assertEquals(
        PersonaInPrompt(name = "Wren", description = "A night-shift archivist.", pronouns = pronouns),
        library.prompts.persona(persona.id),
      )
      assertNull(library.prompts.persona(Uuid.random()))
    }
  }

  @Test
  fun theListIsOrderedByNameLetterCaseAside() = runTest {
    PersonaLibrary(directory).use { library ->
      listOf("bram", "Ada", "Cleo").forEach { library.created(PersonaFields(name = it)) }

      assertEquals(listOf("Ada", "bram", "Cleo"), library.repository.personas().first().map { it.fields.name })
    }
  }

  @Test
  fun aCreateOrEditWithAnEmptyNameIsRefusedAndWritesNothing() = runTest {
    PersonaLibrary(directory).use { library ->
      assertEquals(PersonaSaveResult.EmptyName, library.repository.create(wren().copy(name = " ")))
      assertEquals(emptyList(), library.repository.personas().first())

      val persona = library.created(wren())
      assertEquals(PersonaSaveResult.EmptyName, library.repository.update(persona.id, wren().copy(name = "")))
      assertEquals(persona, library.stored(persona.id))
    }
  }

  @Test
  fun anEditReplacesEveryField() = runTest {
    PersonaLibrary(directory).use { library ->
      val persona = library.created(wren())
      val edited = PersonaFields(
        name = "Wren Hale",
        description = "Retired.",
        personaLabel = "Wren, later",
        personaNote = "",
        pronouns = Pronouns(subject = "they"),
      )

      assertEquals(PersonaSaveResult.Saved(persona.id), library.repository.update(persona.id, edited))

      assertEquals(Persona(persona.id, edited), library.stored(persona.id))
    }
  }

  @Test
  fun anEditOfAPersonaNoLongerStoredIsGone() = runTest {
    PersonaLibrary(directory).use { library ->
      assertEquals(PersonaSaveResult.Gone, library.repository.update(Uuid.random(), wren()))
    }
  }

  @Test
  fun aDeleteRemovesTheRowAndLeavesATombstone() = runTest {
    PersonaLibrary(directory).use { library ->
      val persona = library.created(wren())

      assertEquals(PersonaDeleteResult.Deleted, library.repository.delete(persona.id))

      assertNull(library.repository.persona(persona.id).first())
      assertEquals(
        listOf(PersonasEntityType.name to RowKey.Row(persona.id)),
        library.database.tombstoneDao().all().map { it.entityType to it.rowKey },
      )
      assertEquals(PersonaDeleteResult.Gone, library.repository.delete(persona.id))
    }
  }

  @Test
  fun aCreateThatFailsPartWayAddsNoPersona() = runTest {
    val faults = ArmedFault(PersonaWritePoint.InsertRowWritten)
    PersonaLibrary(directory, faults = faults).use { library ->
      faults.armed = true

      assertEquals(PersonaSaveResult.Failed, library.repository.create(wren()))

      assertEquals(emptyList(), library.repository.personas().first())
    }
  }

  @Test
  fun anEditThatFailsPartWayLeavesThePersonaAsItWas() = runTest {
    val faults = ArmedFault(PersonaWritePoint.UpdateRowWritten)
    PersonaLibrary(directory, faults = faults).use { library ->
      val persona = library.created(wren())
      faults.armed = true

      assertEquals(PersonaSaveResult.Failed, library.repository.update(persona.id, PersonaFields("Changed")))

      assertEquals(persona, library.stored(persona.id))
    }
  }

  @Test
  fun aDeleteThatFailsPartWayKeepsThePersonaAndLeavesNoTombstone() = runTest {
    val faults = ArmedFault(PersonaWritePoint.DeleteRowRemoved)
    PersonaLibrary(directory, faults = faults).use { library ->
      val persona = library.created(wren())
      faults.armed = true

      assertEquals(PersonaDeleteResult.Failed, library.repository.delete(persona.id))

      assertEquals(persona, library.stored(persona.id))
      assertEquals(emptyList(), library.database.tombstoneDao().all())
    }
  }

  private class ArmedFault(private val failing: PersonaWritePoint) : Faults {
    var armed = false

    override fun reached(point: FaultPoint) {
      if (armed && point == failing) throw FaultInjected(point)
    }
  }
}

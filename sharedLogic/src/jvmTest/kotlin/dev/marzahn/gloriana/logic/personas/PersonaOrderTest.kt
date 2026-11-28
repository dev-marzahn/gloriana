package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.MoveResult
import dev.marzahn.gloriana.logic.storage.Place
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonaOrderTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aMovedPersonaTakesItsNewPlaceAndANewOneStillJoinsTheEnd() = runTest {
    PersonaLibrary(directory).use { library ->
      val (ada, _, cleo) = library.createdNamed("Ada", "Bram", "Cleo")

      assertEquals(MoveResult.Moved, library.repository.order.move(cleo.id, Place.Before(ada.id)))
      library.created(PersonaFields(name = "Dara"))

      assertEquals(listOf("Cleo", "Ada", "Bram", "Dara"), library.names())
    }
  }

  @Test
  fun theOrderSurvivesARestart() = runTest {
    PersonaLibrary(directory).use { library ->
      val (ada) = library.createdNamed("Ada", "Bram", "Cleo")
      library.repository.order.move(ada.id, Place.Last)
    }

    PersonaLibrary(directory).use { reopened ->
      assertEquals(listOf("Bram", "Cleo", "Ada"), reopened.names())
    }
  }

  @Test
  fun aMoveRewritesOnlyTheRowsBetweenItsTwoPlaces() = runTest {
    PersonaLibrary(directory).use { library ->
      val ids = library.createdNamed("Ada", "Bram", "Cleo", "Dara").map { it.id }
      val ends = listOf(ids.first(), ids.last())
      val (bram, cleo) = ids[1] to ids[2]
      val before = library.database.personaDao().placedRows().associateBy { it.id }

      library.repository.order.move(bram, Place.Before(ids.last()))

      val after = library.database.personaDao().placedRows().associateBy { it.id }
      assertEquals(ends.map(before::getValue), ends.map(after::getValue))
      assertEquals(
        listOf(before.getValue(cleo).position, before.getValue(bram).position),
        listOf(after.getValue(bram).position, after.getValue(cleo).position),
      )
    }
  }

  @Test
  fun aMoveAfterADeleteKeepsTheGapOutOfTheOrder() = runTest {
    PersonaLibrary(directory).use { library ->
      val (ada, bram, cleo) = library.createdNamed("Ada", "Bram", "Cleo")
      library.repository.delete(bram.id)

      library.repository.order.move(cleo.id, Place.Before(ada.id))

      assertEquals(listOf(cleo.id, ada.id), library.repository.personas().first().map { it.id })
    }
  }

  @Test
  fun anEditKeepsThePersonaWhereItIs() = runTest {
    PersonaLibrary(directory).use { library ->
      val (ada) = library.createdNamed("Ada", "Bram")

      library.repository.update(ada.id, PersonaFields(name = "Zara"))

      assertEquals(listOf("Zara", "Bram"), library.names())
    }
  }

  @Test
  fun movingAPersonaThatIsGoneSaysSoAndChangesNothing() = runTest {
    PersonaLibrary(directory).use { library ->
      val (ada) = library.createdNamed("Ada", "Bram")
      library.repository.delete(ada.id)

      assertEquals(MoveResult.Gone, library.repository.order.move(ada.id, Place.Last))
      assertEquals(listOf("Bram"), library.names())
    }
  }

  @Test
  fun movingBeforeAPersonaThatIsGoneSaysSoAndChangesNothing() = runTest {
    PersonaLibrary(directory).use { library ->
      val (ada, bram, cleo) = library.createdNamed("Ada", "Bram", "Cleo")
      library.repository.delete(ada.id)

      assertEquals(MoveResult.Gone, library.repository.order.move(cleo.id, Place.Before(ada.id)))
      assertEquals(listOf(bram.id, cleo.id), library.repository.personas().first().map { it.id })
    }
  }

  @Test
  fun movingAPersonaBeforeItselfWritesNothing() = runTest {
    PersonaLibrary(directory).use { library ->
      val (_, bram) = library.createdNamed("Ada", "Bram", "Cleo")
      val before = library.database.personaDao().placedRows()

      assertEquals(MoveResult.Moved, library.repository.order.move(bram.id, Place.Before(bram.id)))

      assertEquals(before, library.database.personaDao().placedRows())
    }
  }

  @Test
  fun aMoveThatFailsPartWayKeepsTheOrderLastSaved() = runTest {
    val faults = ArmedMove()
    PersonaLibrary(directory, faults = faults).use { library ->
      val (ada) = library.createdNamed("Ada", "Bram", "Cleo")
      faults.armed = true

      assertEquals(MoveResult.Failed, library.repository.order.move(ada.id, Place.Last))

      assertEquals(listOf("Ada", "Bram", "Cleo"), library.names())
    }
  }

  private suspend fun PersonaLibrary.createdNamed(vararg names: String): List<Persona> =
    names.map { created(PersonaFields(name = it)) }

  private suspend fun PersonaLibrary.names(): List<String> = repository.personas().first().map { it.fields.name }

  private class ArmedMove : Faults {
    var armed = false

    override fun reached(point: FaultPoint) {
      if (armed && point == PersonaWritePoint.MoveRowsWritten) throw FaultInjected(point)
    }
  }
}

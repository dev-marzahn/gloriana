package dev.marzahn.gloriana.logic.presets

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
import kotlin.uuid.Uuid

class PresetOrderTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun presetsListInTheOrderTheyArrivedNeverByName() = runTest {
    PresetLibrary(directory).use { library ->
      val ids = listOf("only-prompts.json", "minimal.json", "gloriana-groups.json").map { library.imported(it).id }

      assertEquals(ids, library.ids())
    }
  }

  @Test
  fun aMovedPresetKeepsItsPlaceThroughASaveARestartAndANewImport() = runTest {
    val (first, second, third) = PresetLibrary(directory).use { library ->
      val ids = library.importedThree()
      assertEquals(MoveResult.Moved, library.repository.order.move(ids[2], Place.Before(ids[0])))
      library.repository.save(library.stored(ids[2]).copy(name = "Renamed"))
      ids
    }

    PresetLibrary(directory).use { reopened ->
      val fourth = reopened.imported("gloriana-groups.json").id

      assertEquals(listOf(third, first, second, fourth), reopened.ids())
    }
  }

  @Test
  fun movingBeforeAPresetThatIsGoneSaysSoAndChangesNothing() = runTest {
    PresetLibrary(directory).use { library ->
      val (first, second, third) = library.importedThree()
      library.repository.delete(second)

      assertEquals(MoveResult.Gone, library.repository.order.move(third, Place.Before(second)))
      assertEquals(listOf(first, third), library.ids())
    }
  }

  @Test
  fun aMoveThatFailsPartWayKeepsTheOrderLastSaved() = runTest {
    val faults = ArmedMove()
    PresetLibrary(directory, faults = faults).use { library ->
      val ids = library.importedThree()
      faults.armed = true

      assertEquals(MoveResult.Failed, library.repository.order.move(ids.first(), Place.Last))

      assertEquals(ids, library.ids())
    }
  }

  private suspend fun PresetLibrary.importedThree(): List<Uuid> =
    listOf("minimal.json", "odd-order.json", "only-prompts.json").map { imported(it).id }

  private suspend fun PresetLibrary.ids(): List<Uuid> = repository.presets().first().map { it.id }

  private class ArmedMove : Faults {
    var armed = false

    override fun reached(point: FaultPoint) {
      if (armed && point == PresetWritePoint.MoveRowsWritten) throw FaultInjected(point)
    }
  }
}

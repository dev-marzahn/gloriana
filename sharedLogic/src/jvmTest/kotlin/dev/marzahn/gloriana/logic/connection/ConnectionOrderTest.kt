package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.MoveResult
import dev.marzahn.gloriana.logic.storage.Place
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionOrderTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun connectionsListInTheOrderTheyWereMadeNeverByName() = runTest {
    ConnectionLibrary(directory).use { library ->
      library.createdNamed("zeta", "Alpha", "beta")

      assertEquals(listOf("zeta", "Alpha", "beta"), library.names())
    }
  }

  @Test
  fun aMovedConnectionKeepsItsPlaceThroughAnEditARestartAndANewConnection() = runTest {
    ConnectionLibrary(directory).use { library ->
      val (zeta, _, beta) = library.createdNamed("zeta", "Alpha", "beta")
      assertEquals(MoveResult.Moved, library.repository.order.move(beta.id, Place.Before(zeta.id)))
      library.repository.update(zeta.id, draft(name = "zeta", model = "model-2"))
      library.repository.updateSamplers(zeta.id) { it.samplers.copy(temperature = 0.5) }
    }

    ConnectionLibrary(directory).use { reopened ->
      reopened.created(draft(name = "gamma"))

      assertEquals(listOf("beta", "zeta", "Alpha", "gamma"), reopened.names())
    }
  }

  @Test
  fun movingBeforeAConnectionThatIsGoneSaysSoAndChangesNothing() = runTest {
    ConnectionLibrary(directory).use { library ->
      val (_, alpha, beta) = library.createdNamed("zeta", "Alpha", "beta")
      library.repository.delete(alpha.id)

      assertEquals(MoveResult.Gone, library.repository.order.move(beta.id, Place.Before(alpha.id)))
      assertEquals(listOf("zeta", "beta"), library.names())
    }
  }

  @Test
  fun aMoveThatFailsPartWayKeepsTheOrderLastSaved() = runTest {
    val faults = ArmedMove()
    ConnectionLibrary(directory, faults = faults).use { library ->
      val (zeta) = library.createdNamed("zeta", "Alpha", "beta")
      faults.armed = true

      assertEquals(MoveResult.Failed, library.repository.order.move(zeta.id, Place.Last))

      assertEquals(listOf("zeta", "Alpha", "beta"), library.names())
    }
  }

  private suspend fun ConnectionLibrary.createdNamed(vararg names: String) = names.map { created(draft(name = it)) }

  private suspend fun ConnectionLibrary.names(): List<String> = list().map { it.fields.name }

  private class ArmedMove : Faults {
    var armed = false

    override fun reached(point: FaultPoint) {
      if (armed && point == ConnectionWritePoint.MoveRowsWritten) throw FaultInjected(point)
    }
  }
}

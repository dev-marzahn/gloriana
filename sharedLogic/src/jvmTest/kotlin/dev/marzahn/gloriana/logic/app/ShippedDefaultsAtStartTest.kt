package dev.marzahn.gloriana.logic.app

import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.PersonaRepository
import dev.marzahn.gloriana.logic.personas.PersonaWritePoint
import dev.marzahn.gloriana.logic.personas.personasModule
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.presetsModule
import dev.marzahn.gloriana.logic.settings.settingsModule
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.logic.storage.storageModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val nothingLogged = ProblemLog { message, cause -> throw AssertionError("Logged: $message", cause) }

class ShippedDefaultsAtStartTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aFreshInstallHasTheDefaultPersonaAndTheDefaultPresetOnceStarted() = runTest {
    repeat(2) {
      started(Faults.None).use { app ->
        assertEquals(emptySet(), app.missing())

        val personas = app.koin.get<PersonaRepository>()
        val presets = app.koin.get<PresetRepository>()
        assertEquals(listOf("User"), personas.personas().first().map { it.fields.name })
        assertEquals(personas.personas().first().single().id, personas.defaultPersona().first())
        assertEquals(listOf("Default"), presets.presets().first().map { it.name })
        assertEquals(presets.presets().first().single().id, presets.defaultPreset().first())
      }
    }
  }

  @Test
  fun aShippedWriteThatFailsIsLoggedKeepsTheAppRunningAndIsTriedAgainAtTheNextStart() = runTest {
    val personaWriteFails = Faults { point ->
      if (point == PersonaWritePoint.InsertRowWritten) throw FaultInjected(point)
    }
    val logged = mutableListOf<Pair<String, Throwable>>()
    started(personaWriteFails) { message, cause -> logged += message to cause }.use { app ->
      assertEquals(setOf(ShippedDefault.Persona), app.missing())
      assertEquals(
        listOf("The shipped default Persona could not be written; the next start tries again"),
        logged.map { it.first },
      )
      assertIs<FaultInjected>(logged.single().second)

      assertNull(app.koin.get<PersonaRepository>().defaultPersona().first())
      assertEquals(listOf("Default"), app.koin.get<PresetRepository>().presets().first().map { it.name })
    }

    started(Faults.None).use { app ->
      assertEquals(emptySet(), app.missing())
      assertEquals(listOf("User"), app.koin.get<PersonaRepository>().personas().first().map { it.fields.name })
    }
  }

  private suspend fun KoinApplication.missing() = koin.get<ShippedDefaultsAtStart>().missing.await()

  private fun started(faults: Faults, log: ProblemLog = nothingLogged) = koinApplication {
    modules(
      storageModule,
      settingsModule,
      presetsModule,
      personasModule,
      shippedDefaultsModule,
      module {
        single { desktopGlorianaDatabase(directory) }
        single { faults }
        single { log }
      },
    )
  }

  private inline fun KoinApplication.use(block: (KoinApplication) -> Unit) {
    try {
      block(this)
    } finally {
      koin.get<GlorianaDatabase>().close()
      close()
    }
  }
}

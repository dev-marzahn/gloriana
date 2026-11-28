package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.settings.settingsModule
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.logic.storage.storageModule
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class PresetGraphTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun untilChatsExistTheAppsGraphCountsNoChatsUsingAPreset() = runTest {
    val app = koinApplication {
      modules(
        storageModule,
        settingsModule,
        presetsModule,
        module {
          single { desktopGlorianaDatabase(directory) }
          single<Faults> { Faults.None }
        },
      )
    }

    assertEquals(0, app.koin.get<PresetRepository>().chatsUsing(Uuid.random()))

    app.koin.get<ApplicationScope>().coroutineContext.job.children.toList().joinAll()
    app.koin.get<GlorianaDatabase>().close()
    app.close()
  }
}

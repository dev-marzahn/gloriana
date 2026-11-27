package dev.marzahn.gloriana.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import kotlin.test.Test
import kotlin.test.assertEquals

private val httpLoggerPackages = listOf("io.ktor.client.plugins.logging", "okhttp3.logging", "coil3.util.DebugLogger")

private fun KoScope.httpLoggerImports(): List<String> = files.flatMap { file ->
  file.imports.filter { import -> httpLoggerPackages.any(import.name::startsWith) }
    .map { "${file.nameWithExtension}: ${it.name}" }
}

class NoHttpLoggingTest {
  @Test
  fun noAppCodeInstallsHttpLogging() {
    assertEquals(emptyList(), Konsist.scopeFromProduction().httpLoggerImports())
  }

  @Test
  fun anImportOfAnHttpLoggerIsCaught() {
    assertEquals(
      listOf("InstallsKtorLogging.kt: io.ktor.client.plugins.logging.Logging"),
      Konsist.scopeFromDirectory("sharedLogic/src/jvmTest/resources/httpLogging").httpLoggerImports(),
    )
  }
}

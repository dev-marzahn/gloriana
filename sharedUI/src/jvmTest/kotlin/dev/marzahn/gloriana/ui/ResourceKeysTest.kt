package dev.marzahn.gloriana.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceKeysTest {
  private val values: Path = Path.of("src/commonMain/composeResources/values")

  @Test
  fun everyStringKeyIsDefinedInOneFileOnly() {
    val key = Regex("""<(?:string|plurals) name="([^"]+)"""")
    val keys = values.listDirectoryEntries("*.xml").filter(Files::isRegularFile).flatMap { file ->
      key.findAll(file.readText()).map { it.groupValues[1] }.toList()
    }

    assertEquals(emptyList(), keys.groupBy { it }.filterValues { it.size > 1 }.keys.sorted())
  }
}

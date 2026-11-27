package dev.marzahn.gloriana.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import kotlin.test.Test
import kotlin.test.assertEquals

private const val KEYS = "dev.marzahn.gloriana.logic.keys"
private const val CHARACTERS = "dev.marzahn.gloriana.logic.characters"
private val separated = listOf(KEYS to CHARACTERS, CHARACTERS to KEYS)

private fun KoScope.crossings(): List<String> = files.flatMap { file ->
  val filePackage = file.packagee?.name.orEmpty()
  separated.filter { (from, _) -> filePackage == from || filePackage.startsWith("$from.") }
    .flatMap { (_, to) -> file.imports.filter { it.name.startsWith("$to.") } }
    .map { "${file.nameWithExtension}: ${it.name}" }
}

class KeysAndCharactersTest {
  @Test
  fun keysAndCharactersDoNotImportEachOther() {
    assertEquals(emptyList(), Konsist.scopeFromProduction().crossings())
  }

  @Test
  fun anImportAcrossIsCaughtBothWays() {
    assertEquals(
      listOf(
        "CharactersReadKeys.kt: dev.marzahn.gloriana.logic.keys.KeyVault",
        "KeysReadCharacters.kt: dev.marzahn.gloriana.logic.characters.CharacterRepository",
      ),
      Konsist.scopeFromDirectory("sharedLogic/src/jvmTest/resources/keysAndCharacters").crossings().sorted(),
    )
  }
}

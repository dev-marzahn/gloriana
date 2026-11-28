package dev.marzahn.gloriana.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GLORIANA = "dev.marzahn.gloriana"
private const val PERSONAS = "$GLORIANA.logic.personas"
private const val PERSONA_PAGES = "$GLORIANA.ui.personas"
private const val DATABASE_DECLARATION = "$GLORIANA.logic.storage/GlorianaDatabase.kt"

private val withoutLabelOrNote = setOf(
  "$PERSONAS.PersonaInPrompt",
  "$PERSONAS.PersonaPrompts",
  "$PERSONAS.personasModule",
  "$PERSONAS.storage.DefaultPersona",
)

private val personasReference = Regex("""\b${Regex.escape(PERSONAS)}(\.\w+)+""")
private val labelNoteOrRows = Regex("""\b(personaLabel|personaNote|personaDao)\b""")

private val KoFileDeclaration.packageName: String get() = packagee?.name.orEmpty()

private fun KoFileDeclaration.isIn(packageName: String) =
  this.packageName == packageName || this.packageName.startsWith("$packageName.")

private fun KoFileDeclaration.mayReadLabelAndNote() =
  isIn(PERSONAS) || isIn(PERSONA_PAGES) || "$packageName/$nameWithExtension" == DATABASE_DECLARATION

private fun KoFileDeclaration.reachesLabelOrNote(): Boolean {
  val imported = imports.map { it.name }.filter { it.startsWith(PERSONAS) }
  val body = text.lineSequence().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
  val named = personasReference.findAll(body).map { it.value }
  return (imported + named).any { it !in withoutLabelOrNote } || labelNoteOrRows.containsMatchIn(body)
}

private fun KoScope.codeReachingLabelOrNote(): List<String> = files
  .filter { it.isIn(GLORIANA) && !it.mayReadLabelAndNote() }
  .filter { it.reachesLabelOrNote() }
  .map { it.nameWithExtension }
  .sorted()

class PersonaPrivacyTest {
  @Test
  fun onlyPersonaStorageAndPersonaPagesReachTheLabelOrNote() {
    assertEquals(emptyList(), Konsist.scopeFromProduction().codeReachingLabelOrNote())
  }

  @Test
  fun everyWayOtherCodeCouldReachTheLabelOrNoteIsCaught() {
    assertEquals(
      listOf(
        "ChatScreenPassesPersonaLabel.kt",
        "PromptDestructuresPersonaFields.kt",
        "PromptNamesPersonaInFull.kt",
        "PromptPrintsPersonaFields.kt",
        "PromptReadsPersonaLabel.kt",
        "PromptReadsPersonaNote.kt",
        "PromptReadsPersonaRows.kt",
        "SiblingPackageReadsPersonaLabel.kt",
      ),
      Konsist.scopeFromDirectory("sharedLogic/src/jvmTest/resources/personaPrivacy").codeReachingLabelOrNote(),
    )
  }
}

package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetPurge
import dev.marzahn.gloriana.logic.assets.AssetSwitch
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.assets.PurgeRule
import dev.marzahn.gloriana.logic.assets.purgeRuleAtStart
import dev.marzahn.gloriana.logic.debug.DebugSignal
import dev.marzahn.gloriana.logic.debug.DebugSwitch
import dev.marzahn.gloriana.logic.debug.DebugSwitches
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetPurgeTest {
  @TempDir
  lateinit var directory: Path

  private lateinit var library: CharacterLibrary
  private lateinit var purge: AssetPurge

  @BeforeEach
  fun open() {
    library = CharacterLibrary(directory)
    purge = AssetPurge(
      store = library.assets,
      library = LibraryDirectory { directory },
      references = CharacterAssetReferences(library.database.characterDao()),
      lock = library.lock,
      clock = library.updatedAt,
    )
  }

  @AfterEach
  fun close() = library.close()

  @Test
  fun runningNowRemovesOnlyFilesNoCharacterUses() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    val kept = library.importedCharacter(ImporterCorpus.ccv3PngFile)
    library.repository.delete(tamsin.id)

    val removed = purge.run(PurgeRule.Now)

    assertEquals(3, removed)
    val storedHashes = library.storedFiles()
    assertTrue(checkNotNull(kept.avatar).hex in storedHashes)
    assertFalse(checkNotNull(tamsin.avatar).hex in storedHashes)
  }

  @Test
  fun thePurgeNowSwitchRunsTheStartPurgeNowWhetherOrNotOneIsDue() {
    assertEquals(PurgeRule.Now, purgeRuleAtStart(switches(purgeNow = true)) { false })
  }

  @Test
  fun withoutTheSwitchTheStartPurgeRunsOnScheduleOnlyWhenDue() {
    assertEquals(PurgeRule.Scheduled, purgeRuleAtStart(switches(purgeNow = false)) { true })
    assertNull(purgeRuleAtStart(switches(purgeNow = false)) { false })
  }

  private fun switches(purgeNow: Boolean) = object : DebugSwitches {
    override fun isOn(switch: DebugSwitch) = purgeNow && switch == AssetSwitch.PurgeNow

    override fun take(signal: DebugSignal) = false
  }

  @Test
  fun theScheduledPurgeKeepsFilesYoungerThanADay() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    library.repository.delete(tamsin.id)
    library.clock.epochMillis = System.currentTimeMillis()

    assertEquals(0, purge.run(PurgeRule.Scheduled))
    assertTrue(checkNotNull(tamsin.avatar).hex in library.storedFiles())
  }

  @Test
  fun thePurgeWaitsForNoImport() = runTest {
    val asked = CompletableDeferred<Unit>()
    library.importedCharacter(ImporterCorpus.charx)
    val pick = async {
      library.repository.import(listOf(ImporterCorpus.charx), {
        asked.complete(Unit)
        awaitCancellation()
      })
    }
    asked.await()

    assertNull(purge.run(PurgeRule.Now))
    pick.cancelAndJoin()
  }

  @Test
  fun aKeptPreMigrationCopyStopsThePurge() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    library.repository.delete(tamsin.id)
    Files.createDirectories(directory.resolve("pre-migration"))

    assertEquals(0, purge.run(PurgeRule.Now))
  }

  @Test
  fun aPurgeIsDueOnceADayHasPassed() = runTest {
    assertTrue(purge.isDue())
    purge.run(PurgeRule.Scheduled)

    assertFalse(purge.isDue())
  }
}

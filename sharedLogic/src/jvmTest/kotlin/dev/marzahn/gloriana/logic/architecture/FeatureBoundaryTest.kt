package dev.marzahn.gloriana.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import dev.marzahn.gloriana.logic.architecture.BoundaryViolation.OutsideAFeature
import dev.marzahn.gloriana.logic.architecture.BoundaryViolation.UsedByAnotherFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureBoundaryTest {
  @Test
  fun noCodeOutsideAFeatureUsesItsEntitiesOrDaos() {
    assertEquals(emptySet(), featureBoundaryViolations(Konsist.scopeFromProduction()))
  }

  @Test
  fun eachFixtureThatCrossesTheBoundaryIsCaughtForTheTypeItUses() {
    assertEquals(
      setOf(
        UsedByAnotherFeature("LibraryReadsChatDao.kt", CHAT_DAO),
        UsedByAnotherFeature("ChatScreenNamesChatEntity.kt", CHAT_ENTITY),
        UsedByAnotherFeature("ChatListImportsChatPackage.kt", CHAT_ENTITY),
        UsedByAnotherFeature("AndroidReadsChatDao.kt", CHAT_DAO),
        UsedByAnotherFeature("LogicRootReadsChatEntity.kt", CHAT_ENTITY),
        UsedByAnotherFeature("LibraryDatabase.kt", CHAT_ENTITY),
        UsedByAnotherFeature("LibraryDatabase.kt", CHAT_DAO),
        UsedByAnotherFeature("LibraryReadsNestedRow.kt", "dev.marzahn.gloriana.logic.chat.ChatRows.Row"),
        UsedByAnotherFeature("DatabaseWithHelper.kt", CHAT_DAO),
        OutsideAFeature("ScreenEntity.kt", "dev.marzahn.gloriana.ui.ScreenEntity"),
        OutsideAFeature("LogicRootDao.kt", "dev.marzahn.gloriana.logic.LogicRootDao"),
      ),
      featureBoundaryViolations(Konsist.scopeFromDirectory(FIXTURES)),
    )
  }

  @Test
  fun theDatabaseClassAndUseInsideTheFeatureAreAllowed() {
    val flaggedFiles = featureBoundaryViolations(Konsist.scopeFromDirectory(FIXTURES)).map { it.fileName }

    assertTrue(flaggedFiles.none { it in setOf("FixtureDatabase.kt", "ChatRepository.kt", "ChatDao.kt") })
  }

  private companion object {
    const val FIXTURES = "sharedLogic/src/jvmTest/resources/featureBoundary"
    const val CHAT_ENTITY = "dev.marzahn.gloriana.logic.chat.ChatEntity"
    const val CHAT_DAO = "dev.marzahn.gloriana.logic.chat.storage.ChatDao"
  }
}

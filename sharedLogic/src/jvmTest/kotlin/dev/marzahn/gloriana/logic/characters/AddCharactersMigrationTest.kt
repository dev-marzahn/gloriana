package dev.marzahn.gloriana.logic.characters

import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.characters.storage.CharacterWrites
import dev.marzahn.gloriana.logic.chat.storage.CharacterChats
import dev.marzahn.gloriana.logic.chat.storage.ChatRemovals
import dev.marzahn.gloriana.logic.characters.storage.toCharacter
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.MigrationHarness
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.TestClock
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AddCharactersMigrationTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun aVersionOneLibraryKeepsItsRowsAndStoresCharactersAfterTheMigration() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 1) { connection ->
      connection.execSQL(
        "INSERT INTO tombstones (entityType, rowKey, deletedAt, updatedAt) " +
          "VALUES ('chats', x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 100, 101)",
      )
    }
    val content = ImportedCard.JsonOnly(
      parsed = parsedCard(),
      assets = listOf(CharacterAsset("background", "sky", "png", AssetSource.Uri("https://example.com/sky.png"))),
    ).newCharacter()

    val (tombstones, stored) = harness.migrateToLatest { database ->
      val clock = UpdatedAtClock(TestClock(epochMillis = 1_000))
      val tombstones = Tombstones(database, clock)
      val removals = ChatRemovals(database, database.chatRemovalDao(), clock, tombstones, Faults.None)
      val chats = CharacterChats(database.chatReferenceDao(), removals, clock)
      val writes = CharacterWrites(database, UuidV7IdFactory, clock, tombstones, chats, Faults.None)
      val id = writes.insert(content)
      database.tombstoneDao().all().map { it.rowKey } to database.characterDao().rows(id)?.toCharacter()
    }

    assertEquals(listOf(RowKey.Row(Uuid.parse("0192f6a0-d4c3-7a1b-8c2d-3e4f5a6b7c8d"))), tombstones)
    assertEquals(content.fields, stored?.fields)
    assertEquals(content.assets, stored?.assets)
  }

  private fun parsedCard() = dev.marzahn.gloriana.logic.characters.card.CardJsonReader.read(
    """{"spec": "chara_card_v3", "data": {"name": "Wren", "alternate_greetings": ["Hello"],
      "extensions": {"depth_prompt": {"prompt": "note", "depth": 2, "role": "user"}}}}""",
  ).let { (it as dev.marzahn.gloriana.logic.characters.card.CardJsonRead.Card).card }
}

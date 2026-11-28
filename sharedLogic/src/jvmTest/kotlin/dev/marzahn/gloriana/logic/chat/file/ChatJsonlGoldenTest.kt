package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.chat.ChatLibrary
import dev.marzahn.gloriana.logic.files.MemoryDestination
import dev.marzahn.gloriana.logic.personas.PersonaFields
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SILLYTAVERN_CHAT = "/chats/sillytavern-1.19.1.jsonl"

class ChatJsonlGoldenTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun theExportReadsAsTheSameChatAsTheOneThePinnedReleaseSaved() = runTest {
    val exported = jsonLines(sameChatExported())
    val saved = jsonLines(sillyTavernChat())

    assertEquals(saved.size, exported.size)
    assertEquals(saved.first().keys, exported.first().keys)
    assertEquals(saved.drop(1).map(::asRead), exported.drop(1).map(::asRead))
  }

  @Test
  fun aGreetingsSwipesHoldTheNamesFilledWhereTheReleaseKeepsMacros() = runTest {
    val exported = jsonLines(sameChatExported())[1]
    val saved = jsonLines(sillyTavernChat())[1]

    assertEquals(saved.getValue("mes"), exported.getValue("mes"))
    assertEquals(listOf("Hello, {{user}}. I am {{char}}.", "Good evening, {{user}}."), saved.swipeTexts())
    assertEquals(listOf("Hello, Robin. I am Ada.", "Good evening, Robin."), exported.swipeTexts())
  }

  @Test
  fun everyKeyTheExportWritesIsOneThePinnedReleaseWritesThere() = runTest {
    val exported = jsonLines(sameChatExported()).drop(1)
    val saved = jsonLines(sillyTavernChat()).drop(1)

    exported.zip(saved).forEach { (ours, theirs) ->
      assertContainsAll(theirs.keys, ours.keys)
      assertContainsAll(theirs.extraKeys(), ours.extraKeys())
      val ourInfo = ours["swipe_info"]?.jsonArray.orEmpty().map { it.jsonObject }
      val theirInfo = theirs["swipe_info"]?.jsonArray.orEmpty().map { it.jsonObject }
      ourInfo.zip(theirInfo).forEach { (ourSwipe, theirSwipe) -> assertContainsAll(theirSwipe.keys, ourSwipe.keys) }
    }
  }

  @Test
  fun aSwipesExtraHoldsOnlyKeysTheReleaseWritesInItsMessagesExtra() = runTest {
    val exported = jsonLines(sameChatExported()).drop(1)
    val saved = jsonLines(sillyTavernChat()).drop(1)

    exported.zip(saved).forEach { (ours, theirs) ->
      ours["swipe_info"]?.jsonArray.orEmpty().forEach { swipe ->
        assertContainsAll(theirs.extraKeys(), swipe.jsonObject.extraKeys())
      }
    }
  }

  private suspend fun sameChatExported(): ByteArray = ChatLibrary(directory).use { library ->
    val ada = library.character(
      name = "Ada",
      firstMessage = "Hello, {{user}}. I am {{char}}.",
      alternateGreetings = listOf("Good evening, {{user}}."),
    )
    val robin = library.personas.created(PersonaFields(name = "Robin"))
    val chat = library.started(library.newChat(ada).copy(personaId = robin.id))
    val rows = ChatRowsForTests(library, chat)
    rows.swipeOn(library.messages(chat.id).single().id, index = 1, TestSwipe("Good evening, Robin."))
    rows.user("Hello, Ada. Shall we begin?")
    rows.character(
      TestSwipe("Of course. Where to?", reasoning = "Robin seems eager."),
      TestSwipe("A second take: \"Anywhere you like.\""),
    )
    rows.user("(A note to myself, hidden.)", hidden = true)
    rows.character(TestSwipe("Then let us go."))
    MemoryDestination().also { assertEquals(ChatExportResult.Exported, library.export.export(chat.id, it)) }.bytes
  }
}

private data class ReadMessage(
  val name: String,
  val isUser: Boolean,
  val hidden: Boolean,
  val text: String,
  val reasoning: String,
  val shownSwipe: Int?,
  val swipes: List<String>?,
)

private fun asRead(line: JsonObject) = ReadMessage(
  name = line.getValue("name").jsonPrimitive.content,
  isUser = line.getValue("is_user").jsonPrimitive.booleanOrNull == true,
  hidden = line.getValue("is_system").jsonPrimitive.isTruthy(),
  text = line.getValue("mes").jsonPrimitive.content,
  reasoning = line.getValue("extra").jsonObject["reasoning"]?.jsonPrimitive?.content.orEmpty(),
  shownSwipe = line["swipe_id"]?.jsonPrimitive?.content?.toInt(),
  swipes = line["swipes"]?.jsonArray?.map { namesFilledAsGlorianaStoresAGreeting(it.jsonPrimitive.content) },
)

private fun JsonPrimitive.isTruthy(): Boolean = booleanOrNull ?: (isString && content.isNotEmpty())

private fun namesFilledAsGlorianaStoresAGreeting(text: String) =
  text.replace("{{user}}", "Robin").replace("{{char}}", "Ada")

private fun JsonObject.swipeTexts(): List<String> = getValue("swipes").jsonArray.map { it.jsonPrimitive.content }

private fun JsonObject.extraKeys(): Set<String> = getValue("extra").jsonObject.keys

private fun assertContainsAll(theirs: Set<String>, ours: Set<String>) =
  assertTrue(theirs.containsAll(ours), "$ours not all in $theirs")

private fun sillyTavernChat(): ByteArray =
  checkNotNull(ChatJsonlGoldenTest::class.java.getResourceAsStream(SILLYTAVERN_CHAT)).use { it.readBytes() }

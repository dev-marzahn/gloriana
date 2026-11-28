package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.files.CorpusFile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PresetExportTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun anExportChangesOnlyWhatWasEdited() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("sillytavern-full.json").id)
      val entries = preset.content.entries
      val main = entries.single { it.identifier == "main" }
      val jailbreak = entries.single { it.identifier == "jailbreak" }
      val edited = preset.copy(
        content = PresetContent(
          rows = listOf(library.repository.newGroup("Rules")) +
            (entries - main - jailbreak) +
            jailbreak.copy(placement = EntryPlacement.InChat(depth = 0)) +
            main.copy(enabled = false),
          wrappers = preset.content.wrappers
            .with(WrapperPrompt.ContinueNudge, "[Keep going.]")
            .with(WrapperPrompt.PersonaDescription, "[{{persona}}]"),
        ),
      )
      assertEquals(PresetSaveResult.Saved, library.repository.save(edited))

      val exported = library.exported(preset.id)
      val source = PresetCorpus.storedPart("sillytavern-full.json")
      val prompts = promptsByIdentifier(exported)
      val sourcePrompts = promptsByIdentifier(source)
      assertEquals(JsonObject(sourcePrompts.getValue("jailbreak") + inChatAtDepth(0)), prompts.getValue("jailbreak"))
      assertEquals(sourcePrompts - "jailbreak", prompts - "jailbreak")
      assertEquals(
        listOf(
          "personaDescription" to true,
          "charDescription" to false,
          "chatHistory" to true,
          "3f0c9a2e-tone" to true,
          "jailbreak" to true,
          "main" to false,
        ),
        globalOrder(exported).map(::orderPair),
      )
      assertEquals(
        JsonObject(
          mapOf(
            "persona_description_format" to JsonPrimitive("[{{persona}}]"),
            "groups" to JsonArray(listOf(group("Rules", at = 0))),
          ),
        ),
        exported.getValue("gloriana"),
      )
      val editedKeys = setOf("prompts", "prompt_order", "gloriana", "continue_nudge_prompt")
      assertEquals(JsonPrimitive("[Keep going.]"), exported.getValue("continue_nudge_prompt"))
      assertEquals(source - editedKeys, exported - editedKeys)
      assertEquals(orderList(source, "100000"), orderList(exported, "100000"))
    }
  }

  @Test
  fun aReorderKeepsDanglingItemsAndDropsRepeatsSoTheEntriesComeBackAsSaved() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("odd-order.json").id)
      val (main, chatHistory, tail) = preset.content.entries
      library.repository.save(preset.copy(content = preset.content.copy(rows = listOf(chatHistory, tail, main))))

      val exported = library.exported(preset.id)
      val source = orderList(PresetCorpus.json("odd-order.json"), "100000").getValue("order").jsonArray
      val order = orderList(exported, "100000").getValue("order").jsonArray
      assertEquals(listOf(source[0], source[2], source[4], source[1]), order)
      assertEquals(
        listOf("chatHistory" to false, "tail" to true, "main" to true),
        reimported(library, exported).content.entries.map { it.identifier to it.enabled },
      )
    }
  }

  @Test
  fun aDeletedEntryStaysDeletedAfterAnotherImport() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("odd-order.json").id)
      library.repository.save(
        preset.copy(content = preset.content.copy(rows = preset.content.entries.filter { it.identifier != "main" })),
      )

      val exported = library.exported(preset.id)
      val source = orderList(PresetCorpus.json("odd-order.json"), "100000").getValue("order").jsonArray
      assertEquals(listOf(source[0], source[2], source[4]), orderList(exported, "100000").getValue("order").jsonArray)
      assertEquals(
        listOf("chatHistory", "tail"),
        reimported(library, exported).content.entries.map { it.identifier },
      )
    }
  }

  @Test
  fun aFileWithoutAnOrderListGetsTheGlobalOneOnlyOnceTheOrderChanges() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("only-prompts.json").id)
      val entries = preset.content.entries
      val toggled = listOf(entries.first().copy(enabled = false)) + entries.drop(1)
      library.repository.save(preset.copy(content = preset.content.copy(rows = toggled)))

      val lists = library.exported(preset.id).getValue("prompt_order").jsonArray
      assertEquals(1, lists.size)
      assertEquals(
        listOf("main" to false, "aside" to true, "deep" to true, "nodepth" to true),
        lists[0].jsonObject.getValue("order").jsonArray.map { orderPair(it.jsonObject) },
      )
    }
  }

  @Test
  fun aDefaultPersonaWrapperAndNoGroupsLeaveNoGlorianaKey() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)
      val content = preset.content.copy(
        rows = preset.content.entries,
        wrappers = preset.content.wrappers.with(WrapperPrompt.PersonaDescription, "{{persona}}"),
      )
      library.repository.save(preset.copy(content = content))

      assertEquals(null, library.exported(preset.id)["gloriana"])
    }
  }

  @Test
  fun aGroupAddedToAFileWhoseGlorianaKeyIsNoObjectReplacesIt() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("gloriana-not-object.json").id)
      val content = preset.content.copy(rows = preset.content.rows + library.repository.newGroup("End"))
      library.repository.save(preset.copy(content = content))

      assertEquals(
        JsonObject(mapOf("groups" to JsonArray(listOf(group("End", at = 1))))),
        library.exported(preset.id)["gloriana"],
      )
    }
  }

  @Test
  fun anEditKeepsUnknownGlorianaKeysAndALaterPromptOfTheSameIdentifier() = runTest {
    PresetLibrary(directory).use { library ->
      val oddOrder = library.stored(library.imported("odd-order.json").id)
      library.repository.save(oddOrder.copy(content = oddOrder.content.copy(rows = oddOrder.content.entries)))
      val gloriana = library.exported(oddOrder.id).getValue("gloriana").jsonObject
      assertEquals(JsonPrimitive(true), gloriana["MARKER-GLORIANA-KEPT"])
      assertEquals(JsonPrimitive("{{persona}}"), gloriana["persona_description_format"])

      val onlyPrompts = library.stored(library.imported("only-prompts.json").id)
      val renamed = onlyPrompts.content.entries.map { if (it.identifier == "main") it.copy(name = "Renamed") else it }
      library.repository.save(onlyPrompts.copy(content = onlyPrompts.content.copy(rows = renamed)))
      val prompts = library.exported(onlyPrompts.id).getValue("prompts").jsonArray.map { it.jsonObject }
      assertEquals(JsonPrimitive("Renamed"), prompts[0]["name"])
      assertEquals(PresetCorpus.json("only-prompts.json").getValue("prompts").jsonArray[2], prompts[2])
    }
  }

  private suspend fun reimported(library: PresetLibrary, file: JsonObject): Preset {
    val result = library.import.import(CorpusFile("again.json", file.toString().encodeToByteArray()))
    return library.stored(assertIs<PresetImportResult.Imported>(result).id)
  }

  private fun group(name: String, at: Int) = JsonObject(mapOf("name" to JsonPrimitive(name), "at" to JsonPrimitive(at)))

  private fun inChatAtDepth(depth: Int) =
    mapOf("injection_position" to JsonPrimitive(1), "injection_depth" to JsonPrimitive(depth))

  private fun promptsByIdentifier(file: JsonObject): Map<String, JsonObject> =
    file.getValue("prompts").jsonArray.map { it.jsonObject }.associateBy { it.text("identifier") }

  private fun orderList(file: JsonObject, characterId: String): JsonObject =
    file.getValue("prompt_order").jsonArray.map { it.jsonObject }.single { it.text("character_id") == characterId }

  private fun globalOrder(file: JsonObject): List<JsonObject> =
    orderList(file, "100001").getValue("order").jsonArray.map { it.jsonObject }

  private fun orderPair(item: JsonElement): Pair<String, Boolean> =
    item.jsonObject.text("identifier") to item.jsonObject.text("enabled").toBoolean()

  private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
}

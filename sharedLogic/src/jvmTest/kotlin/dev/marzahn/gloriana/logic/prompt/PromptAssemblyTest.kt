package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.CharacterNote
import dev.marzahn.gloriana.logic.characters.NoteRole
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.logic.chat.ChatMessage
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.Swipe
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.connection.api.SectionRole
import dev.marzahn.gloriana.logic.connection.api.SectionRole.Assistant
import dev.marzahn.gloriana.logic.connection.api.SectionRole.System
import dev.marzahn.gloriana.logic.connection.api.SectionRole.User
import dev.marzahn.gloriana.logic.personas.PersonaInPrompt
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.logic.presets.WrapperPrompts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private fun entry(
  identifier: String,
  text: String = "",
  filledByApp: Boolean = false,
  enabled: Boolean = true,
  role: EntryRole = EntryRole.System,
  placement: EntryPlacement = EntryPlacement.Relative,
) = PresetEntry(Uuid.random(), identifier, identifier, text, filledByApp, enabled, role, placement)

private fun marker(identifier: String) = entry(identifier, filledByApp = true)

private fun message(role: MessageRole, text: String, hidden: Boolean = false) =
  ChatMessage(Uuid.random(), 0, role, null, hidden, Swipe(0, text, "", SwipeState.Complete))

private val persona = PersonaInPrompt("Sam", "{{user}} is a sailor.", Pronouns())

private val fullOrder = listOf(
  entry("main", "Write {{char}}'s next reply."),
  marker("worldInfoBefore"),
  marker("personaDescription"),
  marker("charDescription"),
  marker("charPersonality"),
  marker("scenario"),
  marker("dialogueExamples"),
  marker("chatHistory"),
  entry("jailbreak", "Stay in character."),
)

private fun assembled(
  character: CharacterFields,
  entries: List<PresetEntry> = fullOrder,
  messages: List<ChatMessage> = emptyList(),
  wrappers: WrapperPrompts = WrapperPrompts.Defaults,
): List<Pair<SectionRole, String>> =
  assemblePrompt(PromptSource(character, persona, PresetContent(entries, wrappers)), messages)
    .promptSections.map { it.role to it.text }

class PromptAssemblyTest {
  @Test
  fun everyMarkerIsFilledInPresetOrderWithNamesExpanded() {
    val character = CharacterFields(
      name = "Ada",
      description = "{{char}} keeps a lighthouse.",
      personality = "calm",
      scenario = "{{user}} arrives at night.",
      exampleMessages = "<START>{{user}}: Hi\n{{char}}: Hello",
    )

    val sections = assembled(
      character,
      messages = listOf(message(MessageRole.Character, "Welcome, {{user}}."), message(MessageRole.User, "Thanks")),
    )

    assertEquals(
      listOf(
        System to "Write Ada's next reply.",
        System to "Sam is a sailor.",
        System to "Ada keeps a lighthouse.",
        System to "calm",
        System to "Sam arrives at night.",
        System to WrapperPrompt.NewExampleChat.default(),
        System to "Sam: Hi\nAda: Hello",
        System to WrapperPrompt.NewChat.default(),
        Assistant to "Welcome, Sam.",
        User to "Thanks",
        System to "Stay in character.",
      ),
      sections,
    )
  }

  @Test
  fun disabledEntriesBlankFieldsAndLorebooksAddNothing() {
    val entries = listOf(entry("main", "Main", enabled = false)) + fullOrder.drop(1)

    val sections = assembled(CharacterFields(name = "Ada"), entries)

    assertEquals(
      listOf(System to "Sam is a sailor.", System to WrapperPrompt.NewChat.default(), System to "Stay in character."),
      sections,
    )
  }

  @Test
  fun theCharactersOwnPromptsReplaceMainAndPostHistoryKeepingTheOriginalWhereAsked() {
    val character = CharacterFields(
      name = "Ada",
      systemPrompt = "{{original}} Speak like {{char}}.",
      postHistoryInstructions = "Never break the fourth wall.",
    )

    val sections = assembled(character, listOf(fullOrder.first(), fullOrder.last()))

    assertEquals(
      listOf(System to "Write Ada's next reply. Speak like Ada.", System to "Never break the fourth wall."),
      sections,
    )
  }

  @Test
  fun hiddenAndEmptyMessagesAreLeftOutOfTheHistory() {
    val messages = listOf(
      message(MessageRole.User, "Seen"),
      message(MessageRole.Character, "Hidden", hidden = true),
      message(MessageRole.Character, ""),
      message(MessageRole.Character, "Also seen"),
    )

    val sections = assembled(CharacterFields(name = "Ada"), listOf(marker("chatHistory")), messages)

    assertEquals(
      listOf(System to WrapperPrompt.NewChat.default(), User to "Seen", Assistant to "Also seen"),
      sections,
    )
  }

  @Test
  fun inChatSectionsGoBeforeTheLastDepthMessagesAndTheCharacterNoteComesLastAtItsDepth() {
    val character = CharacterFields(name = "Ada", characterNote = CharacterNote("Note for {{char}}", 1, NoteRole.User))
    val entries = listOf(
      marker("chatHistory"),
      entry("deep", "Deep", placement = EntryPlacement.InChat(10)),
      entry("one", "One", role = EntryRole.Assistant, placement = EntryPlacement.InChat(1)),
      entry("end", "End", placement = EntryPlacement.InChat(0)),
    )
    val messages = listOf(message(MessageRole.User, "First"), message(MessageRole.Character, "Second"))

    val sections = assembled(character, entries, messages, WrapperPrompts.Defaults.with(WrapperPrompt.NewChat, ""))

    assertEquals(
      listOf(
        System to "Deep",
        User to "First",
        Assistant to "One",
        User to "Note for Ada",
        Assistant to "Second",
        System to "End",
      ),
      sections,
    )
  }

  @Test
  fun inChatSectionsAreDroppedWhenThePresetSendsNoHistory() {
    val entries = listOf(entry("main", "Main"), entry("note", "Note", placement = EntryPlacement.InChat(0)))

    assertEquals(listOf(System to "Main"), assembled(CharacterFields(name = "Ada"), entries))
  }

  @Test
  fun eachExampleBlockGetsItsOwnHeading() {
    val character = CharacterFields(name = "Ada", exampleMessages = "<start>\nOne\n<START>\n\n<START>Two")

    val sections = assembled(character, listOf(marker("dialogueExamples")))

    val heading = WrapperPrompt.NewExampleChat.default()
    assertEquals(listOf(System to heading, System to "One", System to heading, System to "Two"), sections)
  }
}

private fun WrapperPrompt.default() = WrapperPrompts.Defaults[this]

package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.characters.NoteRole
import dev.marzahn.gloriana.logic.chat.ChatMessage
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.connection.api.PromptSection
import dev.marzahn.gloriana.logic.connection.api.SectionRole
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.FilledWith
import dev.marzahn.gloriana.logic.presets.OverriddenByCharacter
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.WrapperPrompt

private const val ORIGINAL = "{{original}}"
private val exampleStart = Regex("<START>", RegexOption.IGNORE_CASE)

private enum class FieldWrapper(val wrapper: WrapperPrompt, val placeholder: String) {
  Persona(WrapperPrompt.PersonaDescription, "{{persona}}"),
  Personality(WrapperPrompt.PersonalityFormat, "{{personality}}"),
  Scenario(WrapperPrompt.ScenarioFormat, "{{scenario}}"),
}

internal sealed interface Gathered {
  class Fixed(val section: AssembledSection) : Gathered

  class History(val opening: AssembledSection?, val messages: List<AssembledSection>) : Gathered
}

internal class InChatSection(val depth: Int, val section: AssembledSection)

internal class Gathering(val inOrder: List<Gathered>, val inChat: List<InChatSection>)

internal fun gather(source: PromptSource, messages: List<ChatMessage>): Gathering =
  Gatherer(source, messages).gathering()

private class Gatherer(private val source: PromptSource, private val messages: List<ChatMessage>) {
  private val character = source.character
  private val wrappers = source.preset.wrappers

  fun gathering(): Gathering {
    val enabled = source.preset.entries.filter { it.enabled }
    val inOrder = enabled.filter { it.placement == EntryPlacement.Relative }.flatMap(::contents)
    val inChat = enabled.flatMap { entry ->
      val placement = entry.placement as? EntryPlacement.InChat ?: return@flatMap emptyList()
      contents(entry).filterIsInstance<Gathered.Fixed>().map { InChatSection(placement.depth, it.section) }
    }
    return Gathering(inOrder, inChat + listOfNotNull(characterNote()))
  }

  private fun contents(entry: PresetEntry): List<Gathered> {
    val role = entry.role.sectionRole
    val origin = SectionOrigin.Entry(entry.identifier)
    return when (entry.filledWith) {
      FilledWith.ChatMessages -> listOf(history())
      FilledWith.ExampleMessages -> exampleMessages()
      FilledWith.LorebookEntries -> emptyList()
      FilledWith.PersonaDescription -> fixed(role, wrapped(FieldWrapper.Persona, source.persona.description), origin)
      FilledWith.CharacterDescription -> fixed(role, character.description, origin)
      FilledWith.CharacterPersonality -> fixed(role, wrapped(FieldWrapper.Personality, character.personality), origin)
      FilledWith.Scenario -> fixed(role, wrapped(FieldWrapper.Scenario, character.scenario), origin)
      null -> fixed(role, ownText(entry), origin)
    }
  }

  private fun wrapped(field: FieldWrapper, value: String): String =
    if (value.isBlank()) "" else wrappers[field.wrapper].replace(field.placeholder, value)

  private fun ownText(entry: PresetEntry): String = when (entry.overriddenByCharacter) {
    OverriddenByCharacter.MainPrompt -> overridden(entry.text, character.systemPrompt)
    OverriddenByCharacter.PostHistoryInstructions -> overridden(entry.text, character.postHistoryInstructions)
    null -> entry.text
  }

  private fun history() = Gathered.History(
    opening = section(SectionRole.System, wrappers[WrapperPrompt.NewChat], SectionOrigin.NewChat),
    messages = messages.filterNot { it.hidden }.mapNotNull(::message),
  )

  private fun message(message: ChatMessage): AssembledSection? =
    section(message.role.sectionRole, message.shown.text, SectionOrigin.Message(message.id))

  private fun exampleMessages(): List<Gathered> = character.exampleMessages
    .split(exampleStart)
    .filter(String::isNotBlank)
    .flatMap { block ->
      fixed(SectionRole.System, wrappers[WrapperPrompt.NewExampleChat], SectionOrigin.ExampleMessages) +
        fixed(SectionRole.System, block.trim(), SectionOrigin.ExampleMessages)
    }

  private fun characterNote(): InChatSection? = character.characterNote?.let { note ->
    section(note.role.sectionRole, note.text, SectionOrigin.CharacterNote)?.let { InChatSection(note.depth, it) }
  }

  private fun fixed(role: SectionRole, text: String, origin: SectionOrigin): List<Gathered> =
    listOfNotNull(section(role, text, origin)?.let(Gathered::Fixed))

  private fun section(role: SectionRole, text: String, origin: SectionOrigin): AssembledSection? = text
    .takeIf(String::isNotBlank)
    ?.let { AssembledSection(PromptSection(role, it.withNamesFilled(source.names)), origin) }
}

private fun overridden(original: String, override: String): String =
  if (override.isBlank()) original else override.replace(ORIGINAL, original)

private val NoteRole.sectionRole: SectionRole
  get() = when (this) {
    NoteRole.System -> SectionRole.System
    NoteRole.User -> SectionRole.User
    NoteRole.Assistant -> SectionRole.Assistant
  }

private val EntryRole.sectionRole: SectionRole
  get() = when (this) {
    EntryRole.System -> SectionRole.System
    EntryRole.User -> SectionRole.User
    EntryRole.Assistant -> SectionRole.Assistant
  }

private val MessageRole.sectionRole: SectionRole
  get() = when (this) {
    MessageRole.User -> SectionRole.User
    MessageRole.Character -> SectionRole.Assistant
  }

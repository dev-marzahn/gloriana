package dev.marzahn.gloriana.logic.prompt

import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.chat.ChatMessage
import dev.marzahn.gloriana.logic.connection.api.PromptSection
import dev.marzahn.gloriana.logic.personas.PersonaInPrompt
import dev.marzahn.gloriana.logic.presets.PresetContent
import kotlin.uuid.Uuid

class PromptSource(val character: CharacterFields, val persona: PersonaInPrompt, val preset: PresetContent) {
  val names = MacroNames(character.name, persona.name)
}

sealed interface SectionOrigin {
  data class Entry(val identifier: String) : SectionOrigin

  data class Message(val id: Uuid) : SectionOrigin

  data object NewChat : SectionOrigin

  data object ExampleMessages : SectionOrigin

  data object CharacterNote : SectionOrigin
}

class AssembledSection(val section: PromptSection, val origin: SectionOrigin)

class AssembledPrompt(val sections: List<AssembledSection>) {
  val promptSections: List<PromptSection> get() = sections.map { it.section }
}

fun assemblePrompt(source: PromptSource, messages: List<ChatMessage>): AssembledPrompt =
  AssembledPrompt(place(gather(source, messages)))

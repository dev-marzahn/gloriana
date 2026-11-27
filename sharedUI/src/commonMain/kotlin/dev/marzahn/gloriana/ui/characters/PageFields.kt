package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.Pronouns

enum class NoteRole { System, User, Assistant }

sealed interface FieldLabel {
  data object Nickname : FieldLabel

  data object Description : FieldLabel

  data object Personality : FieldLabel

  data object Scenario : FieldLabel

  data object Greeting : FieldLabel

  data class AlternateGreeting(val number: Int) : FieldLabel

  data class GroupGreeting(val number: Int) : FieldLabel

  data object ExampleMessages : FieldLabel

  data object MainPrompt : FieldLabel

  data object PostHistoryInstructions : FieldLabel

  data class CharactersNote(val depth: Int, val role: NoteRole) : FieldLabel

  data object CreatorNotes : FieldLabel
}

data class FieldRow(val label: FieldLabel, val value: String)

fun sentToTheModel(character: Character): List<FieldRow> {
  val fields = character.fields
  val note = fields.characterNote?.takeIf { it.text.isNotBlank() }
  return listOfNotNull(
    row(FieldLabel.Nickname, fields.nickname),
    row(FieldLabel.Description, fields.description),
    row(FieldLabel.Personality, fields.personality),
    row(FieldLabel.Scenario, fields.scenario),
    row(FieldLabel.Greeting, fields.firstMessage),
  ) +
    fields.alternateGreetings.numbered(FieldLabel::AlternateGreeting) +
    fields.groupOnlyGreetings.numbered(FieldLabel::GroupGreeting) +
    listOfNotNull(
      row(FieldLabel.ExampleMessages, fields.exampleMessages),
      row(FieldLabel.MainPrompt, fields.systemPrompt),
      row(FieldLabel.PostHistoryInstructions, fields.postHistoryInstructions),
      note?.let { FieldRow(FieldLabel.CharactersNote(it.depth, noteRole(it.role)), it.text) },
    )
}

fun creatorNotes(character: Character): FieldRow? = row(FieldLabel.CreatorNotes, character.fields.creatorNotes)

fun Pronouns.forms(): List<String> =
  listOfNotNull(subject, objective, possessive, possessivePronoun, reflexive).filter { it.isNotBlank() }

private fun row(label: FieldLabel, value: String): FieldRow? =
  value.takeIf { it.isNotBlank() }?.let { FieldRow(label, it) }

private fun List<String>.numbered(label: (Int) -> FieldLabel): List<FieldRow> =
  mapIndexedNotNull { index, value -> row(label(index + 1), value) }

private fun noteRole(role: String): NoteRole = when (role.trim().lowercase()) {
  "user" -> NoteRole.User
  "assistant" -> NoteRole.Assistant
  else -> NoteRole.System
}

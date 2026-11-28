package dev.marzahn.gloriana.ui.presets

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.FilledWith
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.entry_in_chat
import dev.marzahn.gloriana.ui.resources.entry_in_order
import dev.marzahn.gloriana.ui.resources.filled_character_description
import dev.marzahn.gloriana.ui.resources.filled_character_personality
import dev.marzahn.gloriana.ui.resources.filled_chat_messages
import dev.marzahn.gloriana.ui.resources.filled_example_messages
import dev.marzahn.gloriana.ui.resources.filled_lorebook_entries
import dev.marzahn.gloriana.ui.resources.filled_persona_description
import dev.marzahn.gloriana.ui.resources.filled_scenario
import dev.marzahn.gloriana.ui.resources.role_assistant
import dev.marzahn.gloriana.ui.resources.role_system
import dev.marzahn.gloriana.ui.resources.role_user
import dev.marzahn.gloriana.ui.resources.wrapper_continue
import dev.marzahn.gloriana.ui.resources.wrapper_continue_text
import dev.marzahn.gloriana.ui.resources.wrapper_group_nudge
import dev.marzahn.gloriana.ui.resources.wrapper_group_nudge_text
import dev.marzahn.gloriana.ui.resources.wrapper_impersonation
import dev.marzahn.gloriana.ui.resources.wrapper_impersonation_text
import dev.marzahn.gloriana.ui.resources.wrapper_lorebook_format
import dev.marzahn.gloriana.ui.resources.wrapper_lorebook_format_text
import dev.marzahn.gloriana.ui.resources.wrapper_new_chat
import dev.marzahn.gloriana.ui.resources.wrapper_new_chat_text
import dev.marzahn.gloriana.ui.resources.wrapper_new_example_chat
import dev.marzahn.gloriana.ui.resources.wrapper_new_example_chat_text
import dev.marzahn.gloriana.ui.resources.wrapper_new_group_chat
import dev.marzahn.gloriana.ui.resources.wrapper_new_group_chat_text
import dev.marzahn.gloriana.ui.resources.wrapper_persona_description
import dev.marzahn.gloriana.ui.resources.wrapper_persona_description_text
import dev.marzahn.gloriana.ui.resources.wrapper_personality_format
import dev.marzahn.gloriana.ui.resources.wrapper_personality_format_text
import dev.marzahn.gloriana.ui.resources.wrapper_replace_empty
import dev.marzahn.gloriana.ui.resources.wrapper_replace_empty_text
import dev.marzahn.gloriana.ui.resources.wrapper_scenario_format
import dev.marzahn.gloriana.ui.resources.wrapper_scenario_format_text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val EntryRole.label: StringResource
  get() = when (this) {
    EntryRole.System -> Res.string.role_system
    EntryRole.User -> Res.string.role_user
    EntryRole.Assistant -> Res.string.role_assistant
  }

private val FilledWith.label: StringResource
  get() = when (this) {
    FilledWith.ChatMessages -> Res.string.filled_chat_messages
    FilledWith.LorebookEntries -> Res.string.filled_lorebook_entries
    FilledWith.PersonaDescription -> Res.string.filled_persona_description
    FilledWith.CharacterDescription -> Res.string.filled_character_description
    FilledWith.CharacterPersonality -> Res.string.filled_character_personality
    FilledWith.Scenario -> Res.string.filled_scenario
    FilledWith.ExampleMessages -> Res.string.filled_example_messages
  }

@Composable
fun PresetEntry.filledWithText(): String = filledWith?.let { stringResource(it.label) } ?: name

@Composable
fun PresetEntry.placementLine(): String {
  val role = stringResource(role.label)
  return when (val placement = placement) {
    EntryPlacement.Relative -> stringResource(Res.string.entry_in_order, role)
    is EntryPlacement.InChat -> stringResource(Res.string.entry_in_chat, role, placement.depth)
  }
}

val WrapperPromptsInOrder: List<WrapperPrompt> =
  listOf(WrapperPrompt.PersonaDescription) + WrapperPrompt.entries.filter { it != WrapperPrompt.PersonaDescription }

val WrapperPrompt.title: StringResource
  get() = when (this) {
    WrapperPrompt.PersonaDescription -> Res.string.wrapper_persona_description
    WrapperPrompt.ContinueNudge -> Res.string.wrapper_continue
    WrapperPrompt.Impersonation -> Res.string.wrapper_impersonation
    WrapperPrompt.ScenarioFormat -> Res.string.wrapper_scenario_format
    WrapperPrompt.PersonalityFormat -> Res.string.wrapper_personality_format
    WrapperPrompt.WorldInfoFormat -> Res.string.wrapper_lorebook_format
    WrapperPrompt.NewChat -> Res.string.wrapper_new_chat
    WrapperPrompt.NewGroupChat -> Res.string.wrapper_new_group_chat
    WrapperPrompt.NewExampleChat -> Res.string.wrapper_new_example_chat
    WrapperPrompt.GroupNudge -> Res.string.wrapper_group_nudge
    WrapperPrompt.ReplaceEmptyMessage -> Res.string.wrapper_replace_empty
  }

val WrapperPrompt.purpose: StringResource
  get() = when (this) {
    WrapperPrompt.PersonaDescription -> Res.string.wrapper_persona_description_text
    WrapperPrompt.ContinueNudge -> Res.string.wrapper_continue_text
    WrapperPrompt.Impersonation -> Res.string.wrapper_impersonation_text
    WrapperPrompt.ScenarioFormat -> Res.string.wrapper_scenario_format_text
    WrapperPrompt.PersonalityFormat -> Res.string.wrapper_personality_format_text
    WrapperPrompt.WorldInfoFormat -> Res.string.wrapper_lorebook_format_text
    WrapperPrompt.NewChat -> Res.string.wrapper_new_chat_text
    WrapperPrompt.NewGroupChat -> Res.string.wrapper_new_group_chat_text
    WrapperPrompt.NewExampleChat -> Res.string.wrapper_new_example_chat_text
    WrapperPrompt.GroupNudge -> Res.string.wrapper_group_nudge_text
    WrapperPrompt.ReplaceEmptyMessage -> Res.string.wrapper_replace_empty_text
  }

package dev.marzahn.gloriana.ui.characters

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.field_alternate_greeting
import dev.marzahn.gloriana.ui.resources.field_creator_notes
import dev.marzahn.gloriana.ui.resources.field_description
import dev.marzahn.gloriana.ui.resources.field_examples
import dev.marzahn.gloriana.ui.resources.field_greeting
import dev.marzahn.gloriana.ui.resources.field_group_greeting
import dev.marzahn.gloriana.ui.resources.field_main_prompt
import dev.marzahn.gloriana.ui.resources.field_nickname
import dev.marzahn.gloriana.ui.resources.field_note
import dev.marzahn.gloriana.ui.resources.field_personality
import dev.marzahn.gloriana.ui.resources.field_post_history
import dev.marzahn.gloriana.ui.resources.field_scenario
import dev.marzahn.gloriana.ui.resources.role_assistant
import dev.marzahn.gloriana.ui.resources.role_system
import dev.marzahn.gloriana.ui.resources.role_user
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun labelText(label: FieldLabel): String = when (label) {
  FieldLabel.Nickname -> stringResource(Res.string.field_nickname)
  FieldLabel.Description -> stringResource(Res.string.field_description)
  FieldLabel.Personality -> stringResource(Res.string.field_personality)
  FieldLabel.Scenario -> stringResource(Res.string.field_scenario)
  FieldLabel.Greeting -> stringResource(Res.string.field_greeting)
  is FieldLabel.AlternateGreeting -> stringResource(Res.string.field_alternate_greeting, label.number)
  is FieldLabel.GroupGreeting -> stringResource(Res.string.field_group_greeting, label.number)
  FieldLabel.ExampleMessages -> stringResource(Res.string.field_examples)
  FieldLabel.MainPrompt -> stringResource(Res.string.field_main_prompt)
  FieldLabel.PostHistoryInstructions -> stringResource(Res.string.field_post_history)
  is FieldLabel.CharactersNote -> stringResource(Res.string.field_note, label.depth, roleText(label.role))
  FieldLabel.CreatorNotes -> stringResource(Res.string.field_creator_notes)
}

@Composable
private fun roleText(role: NoteRole): String = stringResource(
  when (role) {
    NoteRole.System -> Res.string.role_system
    NoteRole.User -> Res.string.role_user
    NoteRole.Assistant -> Res.string.role_assistant
  },
)

package dev.marzahn.gloriana.logic.characters.card

import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.CharacterNote
import dev.marzahn.gloriana.logic.characters.Pronouns

internal const val SPEC_V2 = "chara_card_v2"
internal const val SPEC_V3 = "chara_card_v3"
internal const val EXTENSIONS_KEY = "extensions"
internal const val ASSETS_KEY = "assets"
internal const val TAGS_KEY = "tags"
internal const val DEPTH_PROMPT_KEY = "depth_prompt"
internal const val GLORIANA_KEY = "gloriana"
internal const val PRONOUNS_KEY = "pronouns"
internal const val EMBEDDED_LOREBOOK_KEY = "character_book"

internal enum class CardTextField(val key: String, val inCcv1: Boolean, val of: (CharacterFields) -> String) {
  Name("name", inCcv1 = true, { it.name }),
  Description("description", inCcv1 = true, { it.description }),
  Personality("personality", inCcv1 = true, { it.personality }),
  Scenario("scenario", inCcv1 = true, { it.scenario }),
  FirstMessage("first_mes", inCcv1 = true, { it.firstMessage }),
  ExampleMessages("mes_example", inCcv1 = true, { it.exampleMessages }),
  Nickname("nickname", inCcv1 = false, { it.nickname }),
  Creator("creator", inCcv1 = false, { it.creator }),
  CharacterVersion("character_version", inCcv1 = false, { it.characterVersion }),
  CreatorNotes("creator_notes", inCcv1 = false, { it.creatorNotes }),
  SystemPrompt("system_prompt", inCcv1 = false, { it.systemPrompt }),
  PostHistoryInstructions("post_history_instructions", inCcv1 = false, { it.postHistoryInstructions }),
}

internal enum class CardListField(val key: String, val of: (CharacterFields) -> List<String>) {
  AlternateGreetings("alternate_greetings", { it.alternateGreetings }),
  GroupOnlyGreetings("group_only_greetings", { it.groupOnlyGreetings }),
}

internal enum class DepthPromptKey(val key: String) {
  Prompt("prompt"),
  Depth("depth"),
  Role("role"),
}

internal enum class PronounKey(val key: String, val of: (Pronouns) -> String?) {
  Subject("sub", { it.subject }),
  Objective("obj", { it.objective }),
  Possessive("poss", { it.possessive }),
  PossessivePronoun("poss_p", { it.possessivePronoun }),
  Reflexive("ref", { it.reflexive }),
}

internal fun characterFields(
  text: (CardTextField) -> String,
  list: (CardListField) -> List<String>,
  note: CharacterNote?,
) = CharacterFields(
  name = text(CardTextField.Name),
  nickname = text(CardTextField.Nickname),
  description = text(CardTextField.Description),
  personality = text(CardTextField.Personality),
  scenario = text(CardTextField.Scenario),
  firstMessage = text(CardTextField.FirstMessage),
  alternateGreetings = list(CardListField.AlternateGreetings),
  groupOnlyGreetings = list(CardListField.GroupOnlyGreetings),
  exampleMessages = text(CardTextField.ExampleMessages),
  creator = text(CardTextField.Creator),
  characterVersion = text(CardTextField.CharacterVersion),
  creatorNotes = text(CardTextField.CreatorNotes),
  systemPrompt = text(CardTextField.SystemPrompt),
  postHistoryInstructions = text(CardTextField.PostHistoryInstructions),
  characterNote = note,
)

internal fun pronouns(form: (PronounKey) -> String?) = Pronouns(
  subject = form(PronounKey.Subject),
  objective = form(PronounKey.Objective),
  possessive = form(PronounKey.Possessive),
  possessivePronoun = form(PronounKey.PossessivePronoun),
  reflexive = form(PronounKey.Reflexive),
)

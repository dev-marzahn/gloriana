package dev.marzahn.gloriana.logic.presets

enum class WrapperPrompt(internal val key: String, internal val pinnedDefault: String) {
  Impersonation(
    "impersonation_prompt",
    "[Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for " +
      "the writing style of {{user}}. Don't write as {{char}} or system. Don't describe actions of {{char}}.]",
  ),
  ContinueNudge("continue_nudge_prompt", "[Continue your last message without repeating its original content.]"),
  NewChat("new_chat_prompt", "[Start a new Chat]"),
  NewGroupChat("new_group_chat_prompt", "[Start a new group chat. Group members: {{group}}]"),
  NewExampleChat("new_example_chat_prompt", "[Example Chat]"),
  GroupNudge("group_nudge_prompt", "[Write the next reply only as {{char}}.]"),
  WorldInfoFormat("wi_format", "{0}"),
  ScenarioFormat("scenario_format", "{{scenario}}"),
  PersonalityFormat("personality_format", "{{personality}}"),
  ReplaceEmptyMessage("send_if_empty", ""),
  PersonaDescription("persona_description_format", "{{persona}}"),
}

@ConsistentCopyVisibility
data class WrapperPrompts private constructor(internal val changes: Map<WrapperPrompt, String>) {
  operator fun get(prompt: WrapperPrompt): String = changes[prompt] ?: prompt.pinnedDefault

  fun with(prompt: WrapperPrompt, text: String): WrapperPrompts =
    WrapperPrompts(if (text == prompt.pinnedDefault) changes - prompt else changes + (prompt to text))

  companion object {
    val PinnedDefaults = WrapperPrompts(emptyMap())

    internal fun of(texts: Map<WrapperPrompt, String>): WrapperPrompts =
      texts.entries.fold(PinnedDefaults) { prompts, (prompt, text) -> prompts.with(prompt, text) }
  }
}

package dev.marzahn.gloriana.logic.presets

enum class WrapperPrompt(internal val key: String, internal val shippedDefault: String) {
  Impersonation(
    "impersonation_prompt",
    "[Write {{user}}'s next message, in the voice and style {{user}} has used in this chat so far. Write only what " +
      "{{user}} says and does: no narrator, no system notes, and nothing for {{char}}.]",
  ),
  ContinueNudge(
    "continue_nudge_prompt",
    "[Carry on from exactly where your last message stopped. Don't restate what it already says.]",
  ),
  NewChat("new_chat_prompt", "[The story starts here.]"),
  NewGroupChat("new_group_chat_prompt", "[The story starts here. In this scene: {{group}}.]"),
  NewExampleChat("new_example_chat_prompt", "[A sample exchange, to show the style:]"),
  GroupNudge("group_nudge_prompt", "[It is {{char}}'s turn. Write {{char}}'s reply and no one else's.]"),
  WorldInfoFormat("wi_format", "{0}"),
  ScenarioFormat("scenario_format", "{{scenario}}"),
  PersonalityFormat("personality_format", "{{personality}}"),
  ReplaceEmptyMessage("send_if_empty", ""),
  PersonaDescription("persona_description_format", "{{persona}}"),
}

@ConsistentCopyVisibility
data class WrapperPrompts private constructor(internal val changes: Map<WrapperPrompt, String>) {
  operator fun get(prompt: WrapperPrompt): String = changes[prompt] ?: prompt.shippedDefault

  fun with(prompt: WrapperPrompt, text: String): WrapperPrompts =
    WrapperPrompts(if (text == prompt.shippedDefault) changes - prompt else changes + (prompt to text))

  companion object {
    val Defaults = WrapperPrompts(emptyMap())

    internal fun of(texts: Map<WrapperPrompt, String>): WrapperPrompts =
      texts.entries.fold(Defaults) { prompts, (prompt, text) -> prompts.with(prompt, text) }
  }
}

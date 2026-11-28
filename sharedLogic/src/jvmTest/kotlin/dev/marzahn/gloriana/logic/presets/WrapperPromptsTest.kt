package dev.marzahn.gloriana.logic.presets

import kotlin.test.Test
import kotlin.test.assertEquals

class WrapperPromptsTest {
  @Test
  fun theSixFixedWrappersShipGlorianasOwnText() {
    assertEquals(
      mapOf(
        WrapperPrompt.Impersonation to
          "[Write {{user}}'s next message, in the voice and style {{user}} has used in this chat so far. " +
          "Write only what {{user}} says and does: no narrator, no system notes, and nothing for {{char}}.]",
        WrapperPrompt.ContinueNudge to
          "[Carry on from exactly where your last message stopped. Don't restate what it already says.]",
        WrapperPrompt.NewChat to "[The story starts here.]",
        WrapperPrompt.NewGroupChat to "[The story starts here. In this scene: {{group}}.]",
        WrapperPrompt.NewExampleChat to "[A sample exchange, to show the style:]",
        WrapperPrompt.GroupNudge to "[It is {{char}}'s turn. Write {{char}}'s reply and no one else's.]",
      ),
      SIX_FIXED.associateWith { WrapperPrompts.Defaults[it] },
    )
  }

  @Test
  fun aWrapperSetBackToItsShippedTextIsNoLongerAChange() {
    val changed = WrapperPrompts.Defaults.with(WrapperPrompt.NewChat, "[Begin]")

    assertEquals(WrapperPrompts.Defaults,changed.with(WrapperPrompt.NewChat, "[The story starts here.]"))
  }

  private companion object {
    val SIX_FIXED = listOf(
      WrapperPrompt.Impersonation,
      WrapperPrompt.ContinueNudge,
      WrapperPrompt.NewChat,
      WrapperPrompt.NewGroupChat,
      WrapperPrompt.NewExampleChat,
      WrapperPrompt.GroupNudge,
    )
  }
}

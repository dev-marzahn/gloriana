package dev.marzahn.gloriana.logic.prompt

import kotlin.test.Test
import kotlin.test.assertEquals

class MacroNamesTest {
  private val names = MacroNames(character = "Ada", user = "Wren")

  @Test
  fun charAndUserAreFilledInAnyCase() {
    assertEquals("Ada, Wren, Ada, Wren", "{{char}}, {{user}}, {{CHAR}}, {{uSeR}}".withNamesFilled(names))
  }

  @Test
  fun otherMacrosAndBrokenBracesStayAsWritten() {
    assertEquals("{{random}} {char} {{ char }}", "{{random}} {char} {{ char }}".withNamesFilled(names))
  }

  @Test
  fun aNameHoldingAMacroIsNotFilledAgain() {
    assertEquals("{{user}} meets Wren", "{{char}} meets {{user}}".withNamesFilled(names.copy(character = "{{user}}")))
  }
}

package dev.marzahn.gloriana.logic.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CustomBodyTextTest {
  @Test
  fun thePinnedKotlinxReleaseSaysWhatIsWrongAndWhere() {
    assertEquals(notJson(1, 7, JsonFault.ValueMissing), CustomBodyText.parse("""{"a": }"""))
    assertEquals(notJson(1, 9, JsonFault.CharacterNotAllowed), CustomBodyText.parse("""{"a": 1, }"""))
    assertEquals(notJson(1, 10, JsonFault.CommaMissing), CustomBodyText.parse("""{"a": 1 "b": 2}"""))
    assertEquals(notJson(1, 8, JsonFault.BraceMissing), CustomBodyText.parse("""{"a": 1"""))
    assertEquals(notJson(1, 12, JsonFault.BracketMissing), CustomBodyText.parse("""{"a": [1, 2"""))
    assertEquals(notJson(1, 9, JsonFault.QuoteMissing), CustomBodyText.parse("""{"a": "x"""))
    assertEquals(notJson(1, 11, JsonFault.TextAfterTheEnd), CustomBodyText.parse("""{"a": 1} x"""))
    assertEquals(notJson(1, 2, JsonFault.CharacterNotAllowed), CustomBodyText.parse("""{a: 1}"""))
  }

  @Test
  fun aFailureTheRulesDoNotKnowIsSomethingWrong() {
    assertEquals(notJson(1, 6, JsonFault.Other), CustomBodyText.parse("""{"a" 1}"""))
    assertEquals(notJson(1, 2, JsonFault.CharacterNotAllowed), CustomBodyText.parse("""{,}"""))
  }

  @Test
  fun linesAndColumnsCountFromOne() {
    assertEquals(notJson(3, 1, JsonFault.BraceMissing), CustomBodyText.parse("{\n  \"a\": 1\n"))
  }

  @Test
  fun aBareWordTheLenientParserLetsThroughIsRefused() {
    assertEquals(CustomBodyText.NotJson(null, JsonFault.CharacterNotAllowed), CustomBodyText.parse("""{"a": tru}"""))
    assertEquals(CustomBodyText.NotJson(null, JsonFault.CharacterNotAllowed), CustomBodyText.parse("""{"a": [@]}"""))
    assertIs<CustomBodyText.Object>(CustomBodyText.parse("""{"a": [true, false, null, -1.5e3, 0]}"""))
  }

  private fun notJson(line: Int, column: Int, fault: JsonFault) =
    CustomBodyText.NotJson(TextPosition(line, column), fault)
}

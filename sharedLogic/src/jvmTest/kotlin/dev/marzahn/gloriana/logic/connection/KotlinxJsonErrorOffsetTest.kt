package dev.marzahn.gloriana.logic.connection

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KotlinxJsonErrorOffsetTest {
  @Test
  fun thePinnedKotlinxReleaseNamesWhereParsingStopped() {
    assertEquals(6, offsetOf("""{"a": }"""))
    assertEquals(8, offsetOf("""{"a": 1, }"""))
  }

  @Test
  fun aMessageWithoutAnOffsetGivesNone() {
    assertNull(KotlinxJsonErrorOffset.of(SerializationException("something else")))
  }

  private fun offsetOf(text: String): Int? =
    KotlinxJsonErrorOffset.of(assertFailsWith<SerializationException> { Json.parseToJsonElement(text) })
}

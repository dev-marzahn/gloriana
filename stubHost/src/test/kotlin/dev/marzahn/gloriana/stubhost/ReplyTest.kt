package dev.marzahn.gloriana.stubhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyTest {
  @Test
  fun `a segment streams in word pieces that keep their spacing`() {
    assertEquals(listOf("Hi ", "there.\n", "\n", "Bye"), Segment.Text("Hi there.\n\nBye").pieces)
  }

  @Test
  fun `the scripted replies hold a reasoning block, a Status block and a refusal`() {
    assertTrue(Reply.withReasoning.segments.first() is Segment.Reasoning)
    assertTrue("<details><summary>Status</summary>" in Reply.withStatus.segments.single().text)
    assertTrue(Reply.refusal.refused)
    assertFalse(Reply.plain.refused)
  }

  @Test
  fun `a reply's tokens count its pieces after a fixed prompt`() {
    assertEquals(Tokens(input = 10, output = 15, reasoning = 9), Reply.withReasoning.tokens)
    assertEquals(25, Reply.withReasoning.tokens.total)
  }
}

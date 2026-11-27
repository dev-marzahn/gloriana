package dev.marzahn.gloriana.stubhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ModesTest {
  private val modes = Modes(defaultDelay = 7.seconds, defaultSlowPause = 2.seconds)
  private val plain = Outcome.Replied(Reply.plain)

  @Test
  fun `delay pauses only before the answer, for the launch default or the length its name gives`() {
    assertEquals(Mode(plain, startPause = 7.seconds), modes.named("delay"))
    assertEquals(Mode(plain, startPause = 250.milliseconds), modes.named("delay-250"))
  }

  @Test
  fun `slow pauses only between stream events, for the launch default or the length its name gives`() {
    assertEquals(Mode(plain, eventPause = 2.seconds), modes.named("slow"))
    assertEquals(Mode(plain, eventPause = 30.milliseconds), modes.named("slow-30"))
  }

  @Test
  fun `the failing modes carry their kind of failure`() {
    mapOf("401" to FailureKind.KEY_REJECTED, "429" to FailureKind.RATE_LIMITED, "500" to FailureKind.HOST_ERROR)
      .forEach { (name, kind) -> assertEquals(kind, assertIs<Outcome.Failed>(modes.named(name)?.outcome).failure.kind) }
    assertEquals(Outcome.EchoesKey(FailureKind.KEY_REJECTED), modes.named("key-echo")?.outcome)
    assertEquals(Outcome.EchoesKey(FailureKind.RATE_LIMITED), modes.named("key-echo-429")?.outcome)
  }

  @Test
  fun `an unknown name or a bad length is no mode`() {
    listOf("fast", "delay-", "delay-soon", "delay--5", "slow-1.5", "normal-2", "").forEach {
      assertNull(modes.named(it), it)
    }
  }

  @Test
  fun `the names list every mode`() {
    assertEquals(
      listOf(
        "normal", "reasoning", "status", "refusal", "length", "delay", "slow", "401", "403", "429", "500", "key-echo",
        "key-echo-429", "stream-error", "cut-off", "stall", "linger", "bad-type", "redirect", "delay-<ms>", "slow-<ms>",
      ),
      modes.names,
    )
  }
}

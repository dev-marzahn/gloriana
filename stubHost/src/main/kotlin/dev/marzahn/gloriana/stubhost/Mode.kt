package dev.marzahn.gloriana.stubhost

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface Outcome {
  data class Replied(val reply: Reply) : Outcome

  data class Failed(val failure: Failure) : Outcome

  data class BreaksOff(val reply: Reply, val failure: Failure) : Outcome

  data class CutsOff(val reply: Reply) : Outcome

  data object Redirects : Outcome

  data class EchoesKey(val kind: FailureKind) : Outcome {
    fun failure(key: String?) = Failure(kind, if (key == null) "No key was sent." else "The key $key is not valid.")
  }
}

internal data class Mode(
  val outcome: Outcome,
  val startPause: Duration = Duration.ZERO,
  val eventPause: Duration = Duration.ZERO,
  val midwayPause: Duration = Duration.ZERO,
  val lingerPause: Duration = Duration.ZERO,
  val malformedContentType: Boolean = false,
)

internal class Modes(defaultDelay: Duration, defaultSlowPause: Duration) {
  private val fixed: Map<String, Mode> = mapOf(
    "normal" to Mode(Outcome.Replied(Reply.plain)),
    "reasoning" to Mode(Outcome.Replied(Reply.withReasoning)),
    "status" to Mode(Outcome.Replied(Reply.withStatus)),
    "refusal" to Mode(Outcome.Replied(Reply.refusal)),
    "length" to Mode(Outcome.Replied(Reply.limited)),
    DELAY to delayed(defaultDelay),
    SLOW to slowed(defaultSlowPause),
    "401" to failing(FailureKind.KEY_REJECTED, "The stub host rejects every key on this path."),
    "403" to failing(FailureKind.FORBIDDEN, "The stub host forbids this key on this path."),
    "429" to failing(FailureKind.RATE_LIMITED, "The stub host is limiting requests on this path."),
    "500" to failing(FailureKind.HOST_ERROR, "The stub host fails every request on this path."),
    "key-echo" to Mode(Outcome.EchoesKey(FailureKind.KEY_REJECTED)),
    "key-echo-429" to Mode(Outcome.EchoesKey(FailureKind.RATE_LIMITED)),
    "stream-error" to Mode(
      Outcome.BreaksOff(Reply.plain, Failure(FailureKind.HOST_ERROR, "The stub host broke off this reply.")),
    ),
    "cut-off" to Mode(Outcome.CutsOff(Reply.plain)),
    "stall" to Mode(Outcome.Replied(Reply.plain), midwayPause = defaultDelay),
    "linger" to Mode(Outcome.Replied(Reply.plain), lingerPause = defaultDelay),
    "bad-type" to Mode(Outcome.Replied(Reply.plain), malformedContentType = true),
    "redirect" to Mode(Outcome.Redirects),
  )

  private val timed: Map<String, (Duration) -> Mode> = mapOf(DELAY to ::delayed, SLOW to ::slowed)

  val names: List<String> = fixed.keys.toList() + timed.keys.map { "$it-<ms>" }

  fun named(name: String): Mode? = fixed[name] ?: timedMode(name)

  private fun timedMode(name: String): Mode? {
    val make = timed[name.substringBefore('-')] ?: return null
    val millis = name.substringAfter('-', missingDelimiterValue = "").toLongOrNull()?.takeIf { it >= 0 }
    return millis?.let { make(it.milliseconds) }
  }

  private companion object {
    const val DELAY = "delay"
    const val SLOW = "slow"

    fun delayed(pause: Duration) = Mode(Outcome.Replied(Reply.plain), startPause = pause)

    fun slowed(pause: Duration) = Mode(Outcome.Replied(Reply.plain), eventPause = pause)

    fun failing(kind: FailureKind, message: String) = Mode(Outcome.Failed(Failure(kind, message)))
  }
}

package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.MASK
import dev.marzahn.gloriana.logic.connection.Samplers
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class StubHostReplyTest {
  @TempDir
  lateinit var directory: Path

  private val stub by lazy { StubHostReplies(directory) }

  @AfterEach
  fun stopHost() = stub.close()

  private fun events(format: ApiFormat, mode: String, streams: Boolean = true) =
    stub.events(stub.connection(format, mode, fields = streaming(streams)))

  @Test
  fun aReplyArrivesAsTextUsageAndAFinishInEveryFormatStreamedOrWhole() {
    everyWay.forEach { (format, streams) ->
      val events = events(format, "normal", streams)

      val way = "$format streaming=$streams"
      assertEquals(PLAIN, events.text(), way)
      assertEquals(listOf(ModelEvent.Usage(10, 10), ModelEvent.Finish(FinishReason.Completed)), events.takeLast(2), way)
    }
  }

  @Test
  fun reasoningArrivesApartFromTheText() {
    everyWay.forEach { (format, streams) ->
      val events = events(format, "reasoning", streams)

      val way = "$format streaming=$streams"
      assertEquals("The user greeted me, so I greet them back.", events.reasoning(), way)
      assertEquals("Hello again from the stub host.", events.text(), way)
      assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last(), way)
    }
  }

  @Test
  fun aRefusalFinishesAsRefusedWithItsText() {
    everyWay.forEach { (format, streams) ->
      val events = events(format, "refusal", streams)

      val way = "$format streaming=$streams"
      assertEquals("I can't help with that request.", events.text(), way)
      assertEquals(ModelEvent.Finish(FinishReason.Refused), events.last(), way)
    }
  }

  @Test
  fun aReplyCutAtTheTokenLimitFinishesAtMaxTokens() {
    everyWay.forEach { (format, streams) ->
      val events = events(format, "length", streams)

      val way = "$format streaming=$streams"
      assertEquals("This reply stops at the token limit", events.text(), way)
      assertEquals(ModelEvent.Finish(FinishReason.MaxTokens), events.last(), way)
    }
  }

  @Test
  fun aStreamThatStopsWithoutItsLastEventIsUnfinished() {
    ApiFormat.entries.forEach { format ->
      val events = events(format, "cut-off")

      assertEquals(PLAIN, events.text(), "$format")
      assertEquals(ModelEvent.Finish(FinishReason.Unfinished), events.last(), "$format")
    }
  }

  @Test
  fun anErrorInTheStreamFailsTheReplyAfterWhatArrived() {
    ApiFormat.entries.forEach { format ->
      val events = events(format, "stream-error")

      assertEquals(PLAIN, events.text(), "$format")
      val failure = assertIs<ModelApiError.HostError>(events.failure(), "$format")
      assertNull(failure.status)
      assertContains(failure.body, "The stub host broke off this reply.")
      assertFalse(events.any { it is ModelEvent.Finish }, "$format")
    }
  }

  @Test
  fun eachHostStatusIsItsOwnKindOfError() {
    everyWay.forEach { (format, streams) ->
      fun failure(mode: String) = events(format, mode, streams).single().let { assertIs<ModelEvent.Failed>(it).error }

      val way = "$format streaming=$streams"
      assertEquals(401, assertIs<ModelApiError.KeyRejected>(failure("401"), way).status)
      val forbidden = assertIs<ModelApiError.KeyRejected>(failure("403"), way)
      assertEquals(403, forbidden.status)
      assertContains(forbidden.body, "forbids this key")
      val limited = assertIs<ModelApiError.RateLimited>(failure("429"), way)
      assertEquals(1, limited.retryAfterSeconds)
      assertContains(limited.body, "limiting requests")
      val hostError = assertIs<ModelApiError.HostError>(failure("500"), way)
      assertEquals(500, hostError.status)
      assertContains(hostError.body, "fails every request")
    }
  }

  @Test
  fun aRedirectIsNotFollowedAndIsAHostError() {
    everyWay.forEach { (format, streams) ->
      val failure = events(format, "redirect", streams).failure()

      assertEquals(307, assertIs<ModelApiError.HostError>(failure, "$format streaming=$streams").status)
    }
    assertTrue(stub.recordedRequests().none { "/normal/" in it.getValue("path").toString() })
  }

  @Test
  fun aKeyTheHostEchoesInItsBodyIsMasked() {
    everyWay.forEach { (format, streams) ->
      val body = assertIs<ModelApiError.KeyRejected>(events(format, "key-echo", streams).failure()).body

      assertContains(body, "The key $MASK is not valid.", message = "$format streaming=$streams")
      assertFalse(TEST_KEY in body)
    }
  }

  @Test
  fun aPaddedCustomKeyHeaderGoesOutTrimmedAndIsMasked() {
    val proxy = stub.connection(
      ApiFormat.OpenAiChatCompletions,
      "key-echo",
      key = null,
      headers = listOf(header("Authorization", "  Bearer proxy-MARKER-2\t")),
    )

    val body = assertIs<ModelApiError.KeyRejected>(stub.events(proxy).failure()).body

    assertContains(body, "The key $MASK is not valid.")
    assertFalse("proxy-MARKER-2" in body)
  }

  @Test
  fun aKeyTheHostEchoesInARateLimitBodyIsMasked() {
    everyWay.forEach { (format, streams) ->
      val failure = events(format, "key-echo-429", streams).failure()

      val body = assertIs<ModelApiError.RateLimited>(failure, "$format streaming=$streams").body
      assertContains(body, "The key $MASK is not valid.")
      assertFalse(TEST_KEY in body)
    }
  }

  @Test
  fun aCustomKeyHeaderTheHostEchoesIsMaskedToo() {
    val proxy = stub.connection(
      ApiFormat.OpenAiChatCompletions,
      "key-echo",
      key = null,
      headers = listOf(header("Authorization", "Bearer proxy-MARKER-2")),
    )

    val body = assertIs<ModelApiError.KeyRejected>(stub.events(proxy).failure()).body

    assertContains(body, "The key $MASK is not valid.")
    assertFalse("proxy-MARKER-2" in body)
  }

  @Test
  fun aHostSilentPastTheTimeoutIsATimeout() {
    ApiFormat.entries.forEach { format ->
      val events = stub.events(stub.connection(format, "delay", fields = { it.copy(timeoutSeconds = 1) }))

      assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.Timeout)), events, "$format")
    }
  }

  @Test
  fun aStreamThatFallsSilentMidwayKeepsItsTextAndEndsInATimeout() {
    ApiFormat.entries.forEach { format ->
      val events = stub.events(stub.connection(format, "stall", fields = { it.copy(timeoutSeconds = 1) }))

      val text = events.dropLast(1)
      assertTrue(text.isNotEmpty() && text.all { it is ModelEvent.TextDelta }, "$format: $events")
      assertTrue(PLAIN.startsWith(events.text()), "$format")
      assertEquals(ModelEvent.Failed(ModelApiError.Timeout), events.last(), "$format")
    }
  }

  @Test
  fun aHostThatHoldsTheConnectionAfterTheLastEventStillFinishesAtOnce() {
    ApiFormat.entries.forEach { format ->
      val started = TimeSource.Monotonic.markNow()

      val events = stub.events(stub.connection(format, "linger", fields = { it.copy(timeoutSeconds = 10) }))

      assertEquals(PLAIN, events.text(), "$format")
      assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last(), "$format")
      assertEquals(1, events.count { it is ModelEvent.Finish || it is ModelEvent.Failed }, "$format")
      assertTrue(started.elapsedNow() < 2.seconds, "$format took ${started.elapsedNow()}, the host lingers 3 s")
    }
  }

  @Test
  fun aShortKeyThatAppearsInASuccessfulReplyLeavesTheReplyIntact() {
    val shortKeys = listOf("Hello", "o", "text", "type", "e")
    everyWay.forEach { (format, streams) ->
      shortKeys.forEach { key ->
        val events = stub.events(stub.connection(format, "normal", key = key, fields = streaming(streams)))

        val way = "$format streaming=$streams key=$key"
        assertEquals(PLAIN, events.text(), way)
        assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last(), way)
      }
    }
    val bearerE = listOf(header("Authorization", "Bearer e"))
    val bearer = stub.connection(ApiFormat.OpenAiChatCompletions, "normal", key = null, headers = bearerE)
    assertEquals(PLAIN, stub.events(bearer).text())
  }

  @Test
  fun aBaseUrlThatDoesNotParseIsRefused() {
    val broken = openedConnection(ApiFormat.OpenAiChatCompletions, baseUrl = "http://h:999999/v1")

    assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.BadAddress)), stub.events(broken))
  }

  @Test
  fun aMalformedResponseContentTypeKeepsTheStatusAndBody() {
    val whole = stub.events(stub.connection(ApiFormat.OpenAiChatCompletions, "bad-type", fields = streaming(false)))
    val streamed = stub.events(stub.connection(ApiFormat.OpenAiChatCompletions, "bad-type"))

    assertEquals(PLAIN, whole.text())
    assertEquals(200, assertIs<ModelApiError.HostError>(streamed.failure()).status)
  }

  @Test
  fun aStreamLongerThanTheTimeoutWithShortPausesCompletes() {
    val slow = stub.connection(ApiFormat.AnthropicMessages, "slow-150", fields = { it.copy(timeoutSeconds = 1) })

    val events = stub.events(slow)

    assertEquals(PLAIN, events.text())
    assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last())
  }

  @Test
  fun aHostNoOneAnswersAtIsUnreachable() {
    val closed = openedConnection(ApiFormat.OpenAiChatCompletions, baseUrl = "http://127.0.0.1:1/v1")

    assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.Unreachable)), stub.events(closed))
  }

  @Test
  fun aCustomBodyStreamThatIsNotAJsonBooleanIsRefused() {
    listOf("""{"stream":"true"}""", """{"stream":{}}""", """{"stream":[]}""").forEach { body ->
      val connection =
        stub.connection(ApiFormat.OpenAiChatCompletions, "normal", fields = { it.copy(customBody = json(body)) })

      val refused = ModelEvent.Failed(ModelApiError.StreamNotBoolean)
      assertEquals(listOf<ModelEvent>(refused), stub.events(connection), body)
    }
    assertEquals(emptyList(), stub.recordedRequests())
  }

  @Test
  fun aHeaderTheAppMustSetOrAValueHttpCannotCarryIsRefusedBeforeSending() {
    listOf(
      header("Accept-Encoding", "br"),
      header("X-Org", "café"),
      header("Content-Type", "json"),
      header("X Org", "v"),
    ).forEach { bad ->
      val connection = stub.connection(ApiFormat.OpenAiChatCompletions, "normal", headers = listOf(bad))

      val refused = ModelEvent.Failed(ModelApiError.BadHeader)
      assertEquals(listOf<ModelEvent>(refused), stub.events(connection), bad.name.canonical)
    }
    assertEquals(emptyList(), stub.recordedRequests())
  }

  @Test
  fun aCustomContentTypeIsTheBodysType() {
    val contentType = header("Content-Type", "application/vnd.host+json")
    val typed = stub.connection(ApiFormat.OpenAiChatCompletions, "normal", headers = listOf(contentType))

    stub.events(typed)

    assertEquals("application/vnd.host+json", stub.recordedRequests().single().recordedHeader("content-type"))
  }

  @Test
  fun theHostGetsTheKeyHeaderMergedSamplersAndCustomHeaders() {
    val anthropic = stub.connection(ApiFormat.AnthropicMessages, "normal", headers = listOf(header("X-Org", "org-1")))

    stub.events(anthropic, global = Samplers(temperature = 0.5, topK = 30, seed = 9))

    val recorded = stub.recordedRequests().single()
    assertEquals(TEST_KEY, recorded.recordedHeader("x-api-key"))
    assertEquals("2023-06-01", recorded.recordedHeader("anthropic-version"))
    assertEquals("org-1", recorded.recordedHeader("x-org"))
    val body = recorded.getValue("body").jsonObject
    assertEquals(JsonPrimitive(0.5), body["temperature"])
    assertEquals(JsonPrimitive(30), body["top_k"])
    assertNull(body["seed"])
  }

  @Test
  fun aConnectionWithNoKeySendsNoKeyHeader() {
    ApiFormat.entries.forEach { format -> stub.events(stub.connection(format, "normal", key = null)) }

    stub.recordedRequests().forEach { recorded ->
      assertNull(recorded.recordedHeader("x-api-key"))
      assertNull(recorded.recordedHeader("authorization"))
    }
  }
}

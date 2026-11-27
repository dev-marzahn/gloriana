package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.entered
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ConnectionTestTest {
  @TempDir
  lateinit var directory: Path

  private val harness by lazy { ProbeHarness(directory) }

  @AfterEach
  fun stop() = harness.close()

  private fun test(format: ApiFormat, mode: String, streams: Boolean = true) = runBlocking {
    harness.probe().test(harness.draftFor(format, mode, fields = streaming(streams)), stored = null)
  }

  private fun failure(format: ApiFormat, mode: String, streams: Boolean = true): ModelApiError =
    assertIs<ProbeResult.Failed>(test(format, mode, streams), "$format $mode streaming=$streams").error

  @Test
  fun aTestReturnsTheHostsReplyInEveryFormatStreamedOrWhole() {
    everyWay.forEach { (format, streams) ->
      assertEquals(ProbeResult.Done(PLAIN), test(format, "normal", streams), "$format streaming=$streams")
    }
  }

  @Test
  fun aTestSendsOneShortRequestWithTheConnectionsSettings() {
    ApiFormat.entries.forEach { format -> test(format, "normal") }

    val requests = harness.stub.recordedRequests()
    assertEquals(ApiFormat.entries.size, requests.size)
    requests.forEach { request ->
      val body = request.getValue("body").jsonObject
      assertEquals(JsonPrimitive("model-1"), body["model"])
      assertContains(body.toString(), testPrompt.single().text)
      assertEquals("POST", request.getValue("method").jsonPrimitive.content)
    }
  }

  @Test
  fun eachErrorKindFailsTheTestInEveryFormat() {
    val expected = mapOf(
      "401" to ModelApiError.KeyRejected::class,
      "403" to ModelApiError.KeyRejected::class,
      "429" to ModelApiError.RateLimited::class,
      "500" to ModelApiError.HostError::class,
      "stream-error" to ModelApiError.HostError::class,
      "redirect" to ModelApiError.HostError::class,
    )
    everyWay.forEach { (format, streams) ->
      expected.forEach { (mode, kind) ->
        assertEquals(kind, failure(format, mode, streams)::class, "$format $mode streaming=$streams")
      }
    }
  }

  @Test
  fun aStreamFlagThatIsNoBooleanFailsBeforeAnyRequest() = runBlocking {
    val draft = harness.draftFor(ApiFormat.OpenAiChatCompletions, "normal") {
      it.copy(customBody = json("""{"stream":"yes"}"""))
    }

    assertEquals(ProbeResult.Failed(ModelApiError.StreamNotBoolean), harness.probe().test(draft, stored = null))
    assertEquals(emptyList(), harness.stub.recordedRequests())
  }

  @Test
  fun aHostThatDoesNotAnswerInTimeOrAtAllFails() = runBlocking {
    val probe = harness.probe()
    ApiFormat.entries.forEach { format ->
      val slow = harness.draftFor(format, "delay-3000") { it.copy(timeoutSeconds = 1) }
      assertEquals(ProbeResult.Failed(ModelApiError.Timeout), probe.test(slow, stored = null), "$format")
      val closed = harness.draftFor(format, "normal") { it.copy(baseUrl = "http://127.0.0.1:1/v1") }
      assertEquals(ProbeResult.Failed(ModelApiError.Unreachable), probe.test(closed, stored = null), "$format")
    }
  }

  @Test
  fun whatTheSaveWouldRefuseIsRefusedBeforeAnyRequest() = runBlocking {
    val probe = harness.probe()
    val format = ApiFormat.OpenAiResponses
    val refusals = mapOf(
      harness.draftFor(format, "normal") { it.copy(timeoutSeconds = 0) } to ConnectionProblem.TimeoutNotPositive,
      harness.draftFor(format, "normal") { it.copy(baseUrl = "api.example.com/v1") } to
        ConnectionProblem.BaseUrlNotHttp,
      harness.draftFor(format, "normal") { it.copy(model = " ") } to ConnectionProblem.ModelEmpty,
      harness.draftFor(format, "normal", headers = listOf(HeaderDraft("Host", entered("x")))) to
        ConnectionProblem.HeaderNameReserved(0),
      harness.draftFor(format, "normal", key = KeyInput.Entered("sk\nnew")) to ConnectionProblem.KeyBadCharacter('\n'),
    )

    refusals.forEach { (draft, problem) ->
      assertEquals(ProbeResult.Refused(setOf(problem)), probe.test(draft, stored = null), "$problem")
    }
    assertEquals(emptyList(), harness.stub.recordedRequests())
  }

  @Test
  fun aNamelessFormStillTestsAndPlainHttpToAPublicHostFailsUnsent() = runBlocking {
    val probe = harness.probe()
    val nameless = harness.draftFor(ApiFormat.AnthropicMessages, "normal") { it.copy(name = "") }
    val public = harness.draftFor(ApiFormat.AnthropicMessages, "normal") { it.copy(baseUrl = "http://8.8.8.8/v1") }

    assertEquals(ProbeResult.Failed(ModelApiError.PlainHttpToPublicHost), probe.test(public, stored = null))
    assertEquals(emptyList(), harness.stub.recordedRequests())
    assertEquals(ProbeResult.Done(PLAIN), probe.test(nameless, stored = null))
  }

  @Test
  fun aKeyTheHostEchoesIsScrubbedFromTheError() {
    everyWay.forEach { (format, streams) ->
      val way = "$format streaming=$streams"
      val rejected = assertIs<ModelApiError.KeyRejected>(failure(format, "key-echo", streams), way).body
      val limited = assertIs<ModelApiError.RateLimited>(failure(format, "key-echo-429", streams), way).body

      listOf(rejected, limited).forEach { body ->
        assertContains(body, "The key $MASK is not valid.", message = way)
        assertFalse(TEST_KEY in body, way)
      }
    }
  }

  @Test
  fun aBaseUrlWithATrailingSlashReachesTheFormatsPath() = runBlocking {
    val draft = harness.draftFor(ApiFormat.AnthropicMessages, "normal") { it.copy(baseUrl = "${it.baseUrl}/") }

    assertEquals(ProbeResult.Done(PLAIN), harness.probe().test(draft, stored = null))
    assertEquals(TEST_KEY, harness.stub.recordedRequests().single().recordedHeader("x-api-key"))
  }
}

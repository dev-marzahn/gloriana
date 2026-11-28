package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.MASK
import dev.marzahn.gloriana.logic.connection.entered
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

private val stubModels = ProbeResult.Done(STUB_MODEL_IDS)
private val noneListed = ProbeResult.Done(emptyList<String>())

class ModelListTest {
  @TempDir
  lateinit var directory: Path

  private val harness by lazy { ProbeHarness(directory) }

  @AfterEach
  fun stop() = harness.close()

  private fun models(format: ApiFormat, mode: String, headers: List<HeaderDraft> = emptyList()) = runBlocking {
    harness.probe().models(harness.draftFor(format, mode, headers = headers), stored = null)
  }

  private fun failure(format: ApiFormat, mode: String): ModelApiError =
    assertIs<ProbeResult.Failed>(models(format, mode), "$format $mode").error

  @Test
  fun eachFormatGetsTheHostsListWithItsKeyAndCustomHeaders() {
    ApiFormat.entries.forEach { format ->
      assertEquals(stubModels, models(format, "normal", listOf(HeaderDraft("X-Org", entered("org-7")))), "$format")
    }

    val requests = harness.stub.recordedRequests()
    assertEquals(ApiFormat.entries.size, requests.size)
    val (anthropic, chat, responses) = requests
    assertEquals("limit=1000", anthropic.getValue("query").jsonPrimitive.content)
    assertEquals(TEST_KEY, anthropic.recordedHeader("x-api-key"))
    assertEquals("2023-06-01", anthropic.recordedHeader("anthropic-version"))
    listOf(chat, responses).forEach { openAi ->
      assertEquals("", openAi.getValue("query").jsonPrimitive.content)
      assertEquals("Bearer $TEST_KEY", openAi.recordedHeader("authorization"))
      assertNull(openAi.recordedHeader("anthropic-version"))
    }
    requests.forEach { request ->
      assertEquals("GET", request.getValue("method").jsonPrimitive.content)
      assertEquals("/normal/v1/models", request.getValue("path").jsonPrimitive.content)
      assertEquals("org-7", request.recordedHeader("x-org"))
      assertEquals(JsonNull, request.getValue("body"))
    }
  }

  @Test
  fun anEmptyListOrAHostWithNoListListsNone() {
    ApiFormat.entries.forEach { format ->
      assertEquals(noneListed, models(format, "empty-list"), "$format")
      assertEquals(noneListed, models(format, "no-list"), "$format")
    }
  }

  @Test
  fun aFailingHostFailsTheListWithTheSharedErrorKinds() {
    ApiFormat.entries.forEach { format ->
      assertEquals(401, assertIs<ModelApiError.KeyRejected>(failure(format, "401"), "$format").status)
      assertEquals(403, assertIs<ModelApiError.KeyRejected>(failure(format, "403"), "$format").status)
      assertIs<ModelApiError.RateLimited>(failure(format, "429"), "$format")
      assertEquals(500, assertIs<ModelApiError.HostError>(failure(format, "500"), "$format").status)
      assertEquals(307, assertIs<ModelApiError.HostError>(failure(format, "redirect"), "$format").status)
    }
  }

  @Test
  fun only404405And501MeanNoList() {
    listOf(404, 405, 501).forEach { status ->
      assertEquals(noneListed, modelListFailure(ModelApiError.HostError(status, "")), "$status")
    }
    listOf(
      ModelApiError.HostError(400, ""),
      ModelApiError.HostError(500, ""),
      ModelApiError.HostError(null, ""),
      ModelApiError.KeyRejected(403, ""),
      ModelApiError.Unreachable,
    ).forEach { error -> assertEquals(ProbeResult.Failed(error), modelListFailure(error), "$error") }
  }

  @Test
  fun aSlowOrClosedHostFailsTheList() = runBlocking {
    val probe = harness.probe()
    ApiFormat.entries.forEach { format ->
      val slow = harness.draftFor(format, "delay-3000") { it.copy(timeoutSeconds = 1) }
      assertEquals(ProbeResult.Failed(ModelApiError.Timeout), probe.models(slow, stored = null), "$format")
      val closed = harness.draftFor(format, "normal") { it.copy(baseUrl = "http://127.0.0.1:1/v1") }
      assertEquals(ProbeResult.Failed(ModelApiError.Unreachable), probe.models(closed, stored = null), "$format")
    }
  }

  @Test
  fun aFormThatCannotSendIsRefusedBeforeAnyRequestButAnEmptyModelIsFine() = runBlocking {
    val probe = harness.probe()
    val format = ApiFormat.OpenAiChatCompletions
    val refusals = mapOf(
      harness.draftFor(format, "normal") { it.copy(baseUrl = "api.example.com/v1") } to
        ConnectionProblem.BaseUrlNotHttp,
      harness.draftFor(format, "normal", headers = listOf(HeaderDraft("Host", entered("x")))) to
        ConnectionProblem.HeaderNameReserved(0),
      harness.draftFor(format, "normal", key = KeyInput.Entered("sk\nnew")) to ConnectionProblem.KeyBadCharacter('\n'),
      harness.draftFor(format, "normal") { it.copy(timeoutSeconds = 0) } to ConnectionProblem.TimeoutNotPositive,
    )
    refusals.forEach { (draft, problem) ->
      assertEquals(ProbeResult.Refused(setOf(problem)), probe.models(draft, stored = null), "$problem")
    }
    val public = harness.draftFor(format, "normal") { it.copy(baseUrl = "http://8.8.8.8/v1") }
    assertEquals(ProbeResult.Failed(ModelApiError.PlainHttpToPublicHost), probe.models(public, stored = null))
    assertEquals(emptyList(), harness.stub.recordedRequests())

    assertEquals(stubModels, probe.models(harness.draftFor(format, "normal") { it.copy(model = "") }, stored = null))
  }

  @Test
  fun aKeyTheHostEchoesIsScrubbedFromTheListsError() {
    ApiFormat.entries.forEach { format ->
      val body = assertIs<ModelApiError.KeyRejected>(failure(format, "key-echo"), "$format").body

      assertContains(body, "The key $MASK is not valid.", message = "$format")
      assertFalse(TEST_KEY in body, "$format")
    }
  }

  @Test
  fun aSuccessfulBodyThatIsNoListIsAHostErrorScrubbedOfTheKey() {
    val scrubber = Scrubber(setOf(TEST_KEY))

    listOf("""{"models":["$TEST_KEY"]}""", "not json $TEST_KEY", """{"data":{"id":"x"}}""").forEach { body ->
      val error = assertIs<ProbeResult.Failed>(modelListOf(body, scrubber), body).error
      val hostError = assertIs<ModelApiError.HostError>(error, body)
      assertNull(hostError.status, body)
      assertFalse(TEST_KEY in hostError.body, body)
    }
  }

  @Test
  fun entriesWithoutAnIdAreSkippedAndRepeatsListedOnce() {
    val body = """{"data":[{"id":"b"},{"name":"no id"},"text",{"id":""},{"id":"a"},{"id":"b"}]}"""

    assertEquals(ProbeResult.Done(listOf("b", "a")), modelListOf(body, Scrubber(emptySet())))
  }
}

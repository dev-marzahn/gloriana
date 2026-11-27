package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.asDraft
import dev.marzahn.gloriana.logic.connection.entered
import dev.marzahn.gloriana.logic.keys.OpenResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val STORED_KEY = "sk-stored-1"
private const val STORED_ORG = "org-stored"

class ProbeDraftTest {
  @TempDir
  lateinit var directory: Path

  private val harness by lazy { ProbeHarness(directory) }

  @AfterEach
  fun stop() = harness.close()

  private suspend fun stored() = harness.library.created(
    harness.draftFor(
      ApiFormat.AnthropicMessages,
      "normal",
      key = KeyInput.Entered(STORED_KEY),
      headers = listOf(HeaderDraft("X-Org", entered(STORED_ORG))),
    ),
  )

  private fun kept(name: String = "X-Org") = HeaderDraft(name, HeaderValueInput.Kept("X-Org"))

  @Test
  fun aConnectionAsSavedSendsItsStoredValues() = runBlocking {
    val stored = stored()

    assertEquals(ProbeResult.Done(PLAIN), harness.probe().test(stored.asDraft(), stored))

    val request = harness.stub.recordedRequests().single()
    assertEquals(STORED_KEY, request.recordedHeader("x-api-key"))
    assertEquals(STORED_ORG, request.recordedHeader("x-org"))
  }

  @Test
  fun valuesTypedAndNotSavedAreSentAndNothingIsStored() = runBlocking {
    val stored = stored()
    val draft = harness.draftFor(
      ApiFormat.AnthropicMessages,
      "normal",
      key = KeyInput.Entered("sk-typed-2"),
      headers = listOf(kept("X-Team"), HeaderDraft("X-Org", entered("org-typed"))),
    ) { it.copy(name = "Renamed", model = "model-typed") }

    assertEquals(ProbeResult.Done(PLAIN), harness.probe().test(draft, stored))
    assertEquals(ProbeResult.Done(STUB_MODEL_IDS), harness.probe().models(draft, stored))

    val requests = harness.stub.recordedRequests()
    assertEquals(2, requests.size)
    requests.forEach { request ->
      assertEquals("sk-typed-2", request.recordedHeader("x-api-key"))
      assertEquals(STORED_ORG, request.recordedHeader("x-team"))
      assertEquals("org-typed", request.recordedHeader("x-org"))
    }
    assertEquals(listOf(stored.id), harness.library.list().map { it.id })
    val after = harness.library.stored(stored.id)
    assertEquals(stored.fields, after.fields)
    assertContentEquals(stored.key?.bytes, after.key?.bytes)
    assertEquals(stored.headers.map { it.name }, after.headers.map { it.name })
    stored.headers.zip(after.headers).forEach { (before, now) ->
      assertContentEquals(before.value.bytes, now.value.bytes)
    }
  }

  @Test
  fun aRemovedKeyAndRemovedHeadersAreNotSent() = runBlocking {
    val stored = stored()

    val draft = harness.draftFor(ApiFormat.AnthropicMessages, "normal", KeyInput.Removed)

    assertEquals(ProbeResult.Done(PLAIN), harness.probe().test(draft, stored))
    val request = harness.stub.recordedRequests().single()
    assertNull(request.recordedHeader("x-api-key"))
    assertNull(request.recordedHeader("x-org"))
  }

  @Test
  fun aNewConnectionHasNoStoredKeyAndAKeptHeaderWithNothingStoredIsGone() = runBlocking {
    val probe = harness.probe()

    val keptOnNew = harness.draftFor(ApiFormat.AnthropicMessages, "normal", KeyInput.Kept)
    assertEquals(ProbeResult.Done(PLAIN), probe.test(keptOnNew, stored = null))
    assertNull(harness.stub.recordedRequests().single().recordedHeader("x-api-key"))

    val orphan = harness.draftFor(ApiFormat.AnthropicMessages, "normal", headers = listOf(kept()))
    val gone = ProbeResult.Refused(setOf(ConnectionProblem.HeaderValueGone(0)))
    assertEquals(gone, probe.test(orphan, stored = null))
    assertEquals(gone, probe.models(orphan, stored = null))
    assertEquals(1, harness.stub.recordedRequests().size)
  }

  @Test
  fun aVaultThatCannotOpenAKeptValueSendsNothing() = runBlocking {
    val stored = stored()
    val draft = harness.draftFor(ApiFormat.AnthropicMessages, "normal", KeyInput.Kept)

    mapOf(
      OpenResult.Missing to ModelApiError.KeysMissing(HeldSecrets.Key),
      OpenResult.Locked to ModelApiError.KeysLocked,
      OpenResult.Unavailable to ModelApiError.KeysUnavailable(HeldSecrets.Key),
    ).forEach { (answer, refusal) ->
      val probe = harness.probe(AnsweringVault(harness.library.vault, answer))
      assertEquals(ProbeResult.Failed(refusal), probe.test(draft, stored), "$answer")
      assertEquals(ProbeResult.Failed(refusal), probe.models(draft, stored), "$answer")
    }
    assertEquals(emptyList(), harness.stub.recordedRequests())
  }
}

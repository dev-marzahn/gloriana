package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionLibrary
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.connection.entered
import dev.marzahn.gloriana.logic.keys.KeyFileResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.OpenResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class VaultModelApiTest {
  @TempDir
  lateinit var directory: Path

  private val stub by lazy { StubHostReplies(directory) }
  private val library by lazy { ConnectionLibrary(directory.resolve("library")) }
  private val settings by lazy { GlobalModelSettings(library.settings) }

  @AfterEach
  fun stop() {
    stub.close()
    library.close()
  }

  private fun api(vault: KeyVault = library.vault) = VaultModelApi(vault, settings, stub.replies)

  private suspend fun stored(key: KeyInput, headers: List<HeaderDraft> = emptyList(), samplers: Samplers = Samplers()) =
    library.created(
      draft(
        baseUrl = stub.host.baseUrl("normal"),
        key = key,
        headers = headers,
        samplers = samplers,
        format = ApiFormat.AnthropicMessages,
      ),
    )

  @Test
  fun theStoredKeyHeadersAndGlobalSamplersReachTheHost() = runBlocking {
    settings.setSamplers(Samplers(temperature = 0.4, topP = 0.6))
    val connection = stored(
      KeyInput.Entered("sk-vault-1"),
      listOf(HeaderDraft("X-Org", entered("org-7"))),
      Samplers(temperature = 0.9),
    )

    val events = api().reply(greeting, connection).toList()

    assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last())
    val recorded = stub.recordedRequests().single()
    assertEquals("sk-vault-1", recorded.recordedHeader("x-api-key"))
    assertEquals("org-7", recorded.recordedHeader("x-org"))
    val body = recorded.getValue("body").jsonObject
    assertEquals(JsonPrimitive(0.9), body["temperature"])
    assertEquals(JsonPrimitive(0.6), body["top_p"])
  }

  @Test
  fun aPaddedKeySavedThroughTheDraftGoesOutTrimmedAndItsEchoIsMasked() = runBlocking {
    everyWay.forEach { (format, streams) ->
      val way = "$format streaming=$streams"
      val padded = draft(
        name = way,
        baseUrl = stub.host.baseUrl("key-echo"),
        key = KeyInput.Entered(" \t$TEST_KEY \t"),
        format = format,
      )
      val connection = library.created(padded.copy(fields = padded.fields.copy(streaming = streams)))

      val failure = api().reply(greeting, connection).toList().filterIsInstance<ModelEvent.Failed>().single()

      val body = assertIs<ModelApiError.KeyRejected>(failure.error, way).body
      assertContains(body, "The key $MASK is not valid.", message = way)
      assertFalse(TEST_KEY in body, way)
    }
  }

  @Test
  fun aBugWhileOpeningTheKeyIsAnUnexpectedFailureNotAnUnreachableHostOrAThrow() = runBlocking {
    val connection = stored(KeyInput.Entered("sk-vault-bug"))

    val events = api(ThrowingVault(library.vault)).reply(greeting, connection).toList()

    assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.Unexpected)), events)
    assertEquals(emptyList(), stub.recordedRequests())
  }

  @Test
  fun aCollectorsOwnThrowIsNotTurnedIntoAFailure() = runBlocking {
    val connection = stored(KeyInput.Entered("sk-vault-3"))

    val thrown = assertFailsWith<IllegalStateException> {
      api().reply(greeting, connection).collect { error("the collector failed") }
    }

    assertEquals("the collector failed", thrown.message)
  }

  @Test
  fun aKeyWhoseDataKeyIsGoneIsRefusedAndNothingIsSent() = runBlocking {
    val connection = stored(KeyInput.Entered("sk-vault-2"))
    assertEquals(KeyFileResult.Done, library.vault.erase(connection.id))

    val events = api().reply(greeting, connection).toList()

    assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.KeysMissing(HeldSecrets.Key))), events)
    assertEquals(emptyList(), stub.recordedRequests())
  }

  @Test
  fun aLockedOrUnavailableVaultIsRefusedAndNothingIsSent() = runBlocking {
    val connection = stored(KeyInput.Removed, listOf(HeaderDraft("X-Org", entered("org-7"))))

    mapOf(
      OpenResult.Locked to ModelApiError.KeysLocked,
      OpenResult.Unavailable to ModelApiError.KeysUnavailable(HeldSecrets.HeaderValues),
    )
      .forEach { (answer, refusal) ->
        val events = api(AnsweringVault(library.vault, answer)).reply(greeting, connection).toList()

        assertEquals(listOf<ModelEvent>(ModelEvent.Failed(refusal)), events, "$answer")
      }
    assertEquals(emptyList(), stub.recordedRequests())
  }
}

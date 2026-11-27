package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionLibrary
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.connection.entered
import dev.marzahn.gloriana.logic.keys.KeyFileResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.keys.SecretSlot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

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
  fun aKeyWhoseDataKeyIsGoneIsRefusedAndNothingIsSent() = runBlocking {
    val connection = stored(KeyInput.Entered("sk-vault-2"))
    assertEquals(KeyFileResult.Done, library.vault.erase(connection.id))

    val events = api().reply(greeting, connection).toList()

    assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.KeysMissing)), events)
    assertEquals(emptyList(), stub.recordedRequests())
  }

  @Test
  fun aLockedOrUnavailableVaultIsRefusedAndNothingIsSent() = runBlocking {
    val connection = stored(KeyInput.Removed, listOf(HeaderDraft("X-Org", entered("org-7"))))

    mapOf(OpenResult.Locked to ModelApiError.KeysLocked, OpenResult.Unavailable to ModelApiError.KeysUnavailable)
      .forEach { (answer, refusal) ->
        val events = api(Answering(library.vault, answer)).reply(greeting, connection).toList()

        assertEquals(listOf<ModelEvent>(ModelEvent.Failed(refusal)), events, "$answer")
      }
    assertEquals(emptyList(), stub.recordedRequests())
  }

  private class Answering(vault: KeyVault, private val answer: OpenResult) : KeyVault by vault {
    override suspend fun open(slot: SecretSlot, secret: SealedSecret): OpenResult = answer
  }
}

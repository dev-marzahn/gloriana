package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class HostCallsTest {
  @TempDir
  lateinit var directory: Path

  private val stub by lazy { StubHostReplies(directory) }

  @AfterEach
  fun stopHost() = stub.close()

  private fun get(path: String): HostRequest {
    val connection = openedConnection(ApiFormat.OpenAiChatCompletions, baseUrl = stub.host.address)
    return checkNotNull(hostRequest(connection, HttpMethod.Get, path, body = null))
  }

  @Test
  fun aGetThatIsNotAChatReplyGoesThroughTheSameCalls() = runBlocking {
    stub.events(stub.connection(ApiFormat.OpenAiChatCompletions, "normal"))

    val reply = assertIs<HostReply.Body>(stub.calls.whole(get("record")))

    assertContains(reply.text, "/normal/v1/chat/completions")
  }

  @Test
  fun aStreamAnsweredWithoutAnEventStreamIsAScrubbedHostError() = runBlocking {
    stub.events(stub.connection(ApiFormat.OpenAiChatCompletions, "normal"))

    val events = stub.calls.events(get("record")).toList()

    val failure = assertIs<ModelApiError.HostError>(assertIs<HostEvent.Failed>(events.single()).error)
    assertEquals(200, failure.status)
    assertContains(failure.body, """"authorization":["$MASK"]""")
    assertFalse(TEST_KEY in failure.body)
  }
}

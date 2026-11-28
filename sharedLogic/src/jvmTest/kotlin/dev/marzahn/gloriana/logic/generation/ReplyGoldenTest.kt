package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.api.TEST_KEY
import dev.marzahn.gloriana.logic.connection.api.json
import dev.marzahn.gloriana.logic.connection.api.recordedHeader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private const val MAIN =
  "This is an ongoing story between Ada and User. Continue it with Ada's next reply, in character."
private const val DESCRIPTION = "Ada keeps a lighthouse."
private const val NEW_CHAT = "[The story starts here.]"

class ReplyGoldenTest {
  @TempDir
  lateinit var directory: Path

  private val harness by lazy { ReplyHarness(directory) }

  @AfterEach
  fun close() = harness.close()

  private fun requestFromAStoredChat(format: ApiFormat): JsonObject = runBlocking {
    val chat = harness.chat(format = format, description = "{{char}} keeps a lighthouse.")
    harness.sent(chat.id)
    harness.settled(chat.id)
    harness.stub.recordedRequests().single()
  }

  @Test
  fun anthropicMessages() {
    val request = requestFromAStoredChat(ApiFormat.AnthropicMessages)

    assertEquals(TEST_KEY, request.recordedHeader("x-api-key"))
    assertEquals(
      json(
        """
        {"model":"model-1","max_tokens":1024,"system":"$MAIN\n\n$DESCRIPTION\n\n$NEW_CHAT",
         "messages":[{"role":"user","content":"Let's get started."},
                     {"role":"assistant","content":"Hello, User. I am Ada."},
                     {"role":"user","content":"Hello, Ada."}],
         "stream":true}
        """,
      ),
      request["body"],
    )
  }

  @Test
  fun openAiChatCompletions() {
    val request = requestFromAStoredChat(ApiFormat.OpenAiChatCompletions)

    assertEquals("Bearer $TEST_KEY", request.recordedHeader("authorization"))
    assertEquals(
      json(
        """
        {"model":"model-1","messages":[{"role":"system","content":"$MAIN"},
           {"role":"system","content":"$DESCRIPTION"},{"role":"system","content":"$NEW_CHAT"},
           {"role":"assistant","content":"Hello, User. I am Ada."},{"role":"user","content":"Hello, Ada."}],
         "max_tokens":1024,"stream":true,"stream_options":{"include_usage":true}}
        """,
      ),
      request["body"],
    )
  }

  @Test
  fun openAiResponses() {
    val request = requestFromAStoredChat(ApiFormat.OpenAiResponses)

    assertEquals("Bearer $TEST_KEY", request.recordedHeader("authorization"))
    assertEquals(
      json(
        """
        {"model":"model-1","instructions":"$MAIN\n\n$DESCRIPTION\n\n$NEW_CHAT",
         "input":[{"role":"assistant","content":"Hello, User. I am Ada."},{"role":"user","content":"Hello, Ada."}],
         "max_output_tokens":1024,"store":false,"stream":true}
        """,
      ),
      request["body"],
    )
  }
}

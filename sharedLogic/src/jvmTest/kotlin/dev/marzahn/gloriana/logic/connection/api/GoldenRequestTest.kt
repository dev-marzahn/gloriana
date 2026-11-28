package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.StreamValue
import dev.marzahn.gloriana.logic.connection.streamValue
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoldenRequestTest {
  private val sections = listOf(
    PromptSection(SectionRole.System, "You are Wren."),
    PromptSection(SectionRole.System, "Scene: a library."),
    PromptSection(SectionRole.User, "Hello"),
    PromptSection(SectionRole.Assistant, "Hi."),
    PromptSection(SectionRole.System, "Keep it short."),
    PromptSection(SectionRole.User, "Tell me more"),
  )
  private val global = Samplers(temperature = 0.3, topP = 0.9)

  private fun golden(fields: ConnectionFields) = fields.copy(
    maxReplyTokens = 512,
    samplers = Samplers(temperature = 0.7),
    customBody = json("""{"metadata":{"user":"u1"}}"""),
    timeoutSeconds = 30,
  )

  private fun request(
    format: ApiFormat,
    key: String? = "sk-1",
    headers: List<HostHeader> = emptyList(),
    sections: List<PromptSection> = this.sections,
    fields: (ConnectionFields) -> ConnectionFields = ::golden,
  ): HostRequest {
    val connection = openedConnection(format, key = key, headers = headers, fields = fields)
    val adapter = format.adapter
    val body = adapter.body(sections, connection.fields, mergeSamplers(format, global, connection.fields.samplers))
    return checkNotNull(hostRequest(connection, HttpMethod.Post, adapter.path, body))
  }

  private fun HostRequest.headerList() = headers.map { it.name.canonical to it.value }

  @Test
  fun anthropicMessages() {
    val request = request(ApiFormat.AnthropicMessages)

    assertEquals("https://host.example/v1/messages", request.url.toString())
    assertEquals(listOf("anthropic-version" to "2023-06-01", "x-api-key" to "sk-1"), request.headerList())
    assertEquals(30, request.timeoutSeconds)
    assertEquals(
      json(
        """
        {"model":"model-1","max_tokens":512,"system":"You are Wren.\n\nScene: a library.",
         "messages":[{"role":"user","content":"Hello"},{"role":"assistant","content":"Hi."},
                     {"role":"user","content":"Keep it short.\n\nTell me more"}],
         "stream":true,"temperature":0.7,"top_p":0.9,"metadata":{"user":"u1"}}
        """,
      ),
      request.body,
    )
  }

  @Test
  fun openAiChatCompletions() {
    val request = request(ApiFormat.OpenAiChatCompletions)

    assertEquals("https://host.example/v1/chat/completions", request.url.toString())
    assertEquals(listOf("authorization" to "Bearer sk-1"), request.headerList())
    assertEquals(
      json(
        """
        {"model":"model-1","messages":[{"role":"system","content":"You are Wren."},
          {"role":"system","content":"Scene: a library."},{"role":"user","content":"Hello"},
          {"role":"assistant","content":"Hi."},{"role":"system","content":"Keep it short."},
          {"role":"user","content":"Tell me more"}],
         "max_tokens":512,"stream":true,"temperature":0.7,"top_p":0.9,"stream_options":{"include_usage":true},
         "metadata":{"user":"u1"}}
        """,
      ),
      request.body,
    )
  }

  @Test
  fun openAiResponses() {
    val request = request(ApiFormat.OpenAiResponses)

    assertEquals("https://host.example/v1/responses", request.url.toString())
    assertEquals(listOf("authorization" to "Bearer sk-1"), request.headerList())
    assertEquals(
      json(
        """
        {"model":"model-1","instructions":"You are Wren.\n\nScene: a library.",
         "input":[{"role":"user","content":"Hello"},{"role":"assistant","content":"Hi."},
                  {"role":"system","content":"Keep it short."},{"role":"user","content":"Tell me more"}],
         "max_output_tokens":512,"store":false,"stream":true,"temperature":0.7,"top_p":0.9,"metadata":{"user":"u1"}}
        """,
      ),
      request.body,
    )
  }

  @Test
  fun anthropicOpensWithAUserTurnWhenTheCharacterSpeaksFirst() {
    val greetingFirst = listOf(
      PromptSection(SectionRole.System, "You are Wren."),
      PromptSection(SectionRole.Assistant, "Welcome to the library."),
      PromptSection(SectionRole.User, "Hello"),
    )

    val messages = request(ApiFormat.AnthropicMessages, sections = greetingFirst).body?.get("messages")

    assertEquals(
      json(
        """
        {"m":[{"role":"user","content":"$OPENING_USER_TURN"},{"role":"assistant","content":"Welcome to the library."},
              {"role":"user","content":"Hello"}]}
        """,
      )["m"],
      messages,
    )
  }

  @Test
  fun aConnectionWithNoKeySendsNoKeyHeader() {
    ApiFormat.entries.forEach { format ->
      val names = request(format, key = null).headers.map { it.name.canonical }
      assertEquals(listOfNotNull("anthropic-version".takeIf { format == ApiFormat.AnthropicMessages }), names)
    }
  }

  @Test
  fun customHeadersComeAfterTheAdaptersOwnAndReplaceOneOfTheSameName() {
    val custom = listOf(header("X-API-KEY", "host-key"), header("X-Org", "o1"))

    val request = request(ApiFormat.AnthropicMessages, headers = custom)

    assertEquals(
      listOf("anthropic-version" to "2023-06-01", "x-api-key" to "host-key", "x-org" to "o1"),
      request.headerList(),
    )
  }

  @Test
  fun streamingOffSendsStreamFalseAndNoStreamOptions() {
    val body = request(ApiFormat.OpenAiChatCompletions) { golden(it).copy(streaming = false) }.body

    assertEquals(StreamValue.Off, body?.streamValue())
    assertNull(body?.get("stream_options"))
  }

  @Test
  fun aCustomBodyThatTurnsStreamingOffDropsStreamOptions() {
    val custom = json("""{"stream":false}""")

    val body = request(ApiFormat.OpenAiChatCompletions) { golden(it).copy(customBody = custom) }.body

    assertEquals(StreamValue.Off, body?.streamValue())
    assertNull(body?.get("stream_options"))
  }

  @Test
  fun theCustomBodyCanTurnStoreOnAndDropIncludeUsage() {
    val custom = json("""{"store":true,"stream_options":{"include_usage":null}}""")

    val stored = request(ApiFormat.OpenAiResponses) { golden(it).copy(customBody = custom) }.body
    val noUsage = request(ApiFormat.OpenAiChatCompletions) { golden(it).copy(customBody = custom) }.body

    assertEquals("true", stored?.get("store").toString())
    assertEquals("{}", noUsage?.get("stream_options").toString())
  }
}

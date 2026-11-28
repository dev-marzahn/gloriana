package dev.marzahn.gloriana.stubhost

import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointsTest {
  private val endpoints = Endpoints(testModes)
  private val normal = Mode(Outcome.Replied(Reply.plain))

  @Test
  fun `labels before the mode give each Connection its own base URL`() {
    assertEquals(
      Target.Model(normal, ChatCompletions),
      endpoints.resolve(HttpMethod.Post, "/summary/normal/v1/chat/completions"),
    )
    assertEquals(
      Target.Model(Mode(Outcome.EchoesKey(FailureKind.KEY_REJECTED)), OpenAiResponses),
      endpoints.resolve(HttpMethod.Post, "/a/b/key-echo/v1/responses"),
    )
    assertEquals(Target.Model(normal, AnthropicMessages), endpoints.resolve(HttpMethod.Post, "/v1/normal/v1/messages"))
  }

  @Test
  fun `a format's path with no mode, an unknown mode or another method has no model`() {
    listOf(
      HttpMethod.Post to "/v1/messages",
      HttpMethod.Post to "normal/v1/messages",
      HttpMethod.Post to "/fast/v1/messages",
      HttpMethod.Get to "/normal/v1/messages",
    ).forEach { (method, path) ->
      assertEquals(Target.NoModel(AnthropicMessages), endpoints.resolve(method, path), "$method $path")
    }
  }

  @Test
  fun `a GET of models in a mode is the model list, anything else there is nowhere`() {
    assertEquals(Target.ModelList(normal), endpoints.resolve(HttpMethod.Get, "/summary/normal/v1/models"))
    listOf(
      HttpMethod.Post to "/normal/v1/models",
      HttpMethod.Get to "/v1/models",
      HttpMethod.Get to "/fast/v1/models",
    ).forEach { (method, path) -> assertEquals(Target.Nowhere, endpoints.resolve(method, path), "$method $path") }
  }

  @Test
  fun `a path that names no format is nowhere`() {
    listOf("/normal/v2/messages", "/normal/v1/completions", "/normal/v1", "/", "/record").forEach {
      assertEquals(Target.Nowhere, endpoints.resolve(HttpMethod.Post, it), it)
    }
  }
}

package dev.marzahn.gloriana.stubhost

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val STREAMED = """{"model":"m","stream":true}"""
private const val WHOLE = """{"model":"m"}"""

class LimitCutAndRedirectTest {
  @TempDir
  lateinit var folder: Path

  private suspend fun ApplicationTestBuilder.streamed(path: String): List<SseEvent> =
    parseSse(client.send(path, STREAMED).bodyAsText())

  private suspend fun ApplicationTestBuilder.whole(path: String) = parseObject(client.send(path, WHOLE).bodyAsText())

  @Test
  fun lengthStopsAtTheTokenLimitInEachFormat() = stubHostTest(folder) {
    assertEquals("max_tokens", whole("/length/v1/messages").text("stop_reason"))
    assertEquals("length", whole("/length/v1/chat/completions").objects("choices").single().text("finish_reason"))
    val response = whole("/length/v1/responses")
    assertEquals("incomplete", response.text("status"))
    assertEquals("max_output_tokens", response.obj("incomplete_details").text("reason"))
    val closing = namedEvents(streamed("/length/v1/responses")).last()
    assertEquals("response.incomplete", closing.text("type"))
  }

  @Test
  fun cutOffStreamsTheTextAndStopsWithoutTheClosingEvents() = stubHostTest(folder) {
    assertFalse(streamed("/cut-off/v1/messages").any { it.name == "message_stop" || it.name == "error" })
    assertFalse(streamed("/cut-off/v1/chat/completions").any { it.data == "[DONE]" || "error" in it.data })
    val closing = setOf("response.completed", "response.failed")
    assertFalse(namedEvents(streamed("/cut-off/v1/responses")).any { it.text("type") in closing })
  }

  @Test
  fun forbiddenIsA403InTheFormatsErrorShape() = stubHostTest(folder) {
    val response = client.send("/403/v1/messages", WHOLE)

    assertEquals(HttpStatusCode.Forbidden, response.status)
    assertEquals("permission_error", parseObject(response.bodyAsText()).obj("error").text("type"))
  }

  @Test
  fun redirectPointsTheSamePathAtTheNormalMode() = stubHostTest(folder) {
    val response = client.config { followRedirects = false }.send("/a/redirect/v1/responses", WHOLE)

    assertEquals(HttpStatusCode.TemporaryRedirect, response.status)
    assertEquals("/a/normal/v1/responses", response.headers[HttpHeaders.Location])
  }
}

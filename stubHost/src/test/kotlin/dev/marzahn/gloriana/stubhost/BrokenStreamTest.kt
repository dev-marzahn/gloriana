package dev.marzahn.gloriana.stubhost

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val BROKE_OFF = "The stub host broke off this reply."
private val PLAIN = Reply.plain.segments.single().text

class BrokenStreamTest {
  @TempDir
  lateinit var folder: Path

  private suspend fun ApplicationTestBuilder.streamed(path: String): List<SseEvent> =
    parseSse(client.send("/stream-error/v1/$path", """{"model":"m","stream":true}""").bodyAsText())

  @Test
  fun anthropicSendsTheTextThenAnErrorEventAndNoStop() = stubHostTest(folder) {
    val events = streamed("messages")

    val last = events.last()
    assertEquals("error", last.name)
    assertEquals(BROKE_OFF, parseObject(last.data).obj("error").text("message"))
    assertFalse(events.any { it.name == "message_delta" || it.name == "message_stop" })
    assertEquals(PLAIN, events.filter { it.name == "content_block_delta" }.joinToString("") {
      parseObject(it.data).obj("delta").text("text")
    })
  }

  @Test
  fun chatCompletionsSendsTheTextThenAnErrorChunkAndNoDone() = stubHostTest(folder) {
    val events = streamed("chat/completions")

    assertEquals(BROKE_OFF, parseObject(events.last().data).obj("error").text("message"))
    assertFalse(events.any { it.data == "[DONE]" })
    val text = events.dropLast(1).mapNotNull {
      parseObject(it.data).objects("choices").single().obj("delta").textOrNull("content")
    }
    assertEquals(PLAIN, text.joinToString(""))
  }

  @Test
  fun responsesSendsTheTextThenResponseFailedAndNoCompleted() = stubHostTest(folder) {
    val events = namedEvents(streamed("responses"))

    val failed = events.last()
    assertEquals("response.failed", failed.text("type"))
    assertEquals("failed", failed.obj("response").text("status"))
    assertEquals(BROKE_OFF, failed.obj("response").obj("error").text("message"))
    assertEquals(events.lastIndex, failed.int("sequence_number"))
    assertFalse(events.any { it.text("type") == "response.completed" })
  }

  @Test
  fun aWholeReplyInThisModeIsTheHostError() = stubHostTest(folder) {
    listOf("messages", "chat/completions", "responses").forEach { path ->
      val response = client.send("/stream-error/v1/$path", """{"model":"m"}""")
      assertEquals(HttpStatusCode.InternalServerError, response.status, path)
    }
  }
}

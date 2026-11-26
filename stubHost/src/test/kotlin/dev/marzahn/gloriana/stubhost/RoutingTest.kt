package dev.marzahn.gloriana.stubhost

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTest {
  @TempDir
  lateinit var folder: Path

  private val howToCall = Endpoints(testModes).howToCall

  @Test
  fun `a path that names no format answers 404 with how to call the stub host`() = stubHostTest(folder) {
    val response = client.send("/normal/v1/completions", "{}")
    assertEquals(HttpStatusCode.NotFound, response.status)
    assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
    assertEquals(howToCall, response.bodyAsText())
  }

  @Test
  fun `a format's path with an unknown mode or method answers 404 in the format's error shape`() =
    stubHostTest(folder) {
      val anthropic = parseObject(client.send("/fast/v1/messages", "{}").bodyAsText())
      assertEquals("not_found_error", anthropic.obj("error").text("type"))
      assertEquals(howToCall, AnthropicReader.errorMessage(anthropic))
      listOf(ChatCompletionsReader, ResponsesReader).forEach { reader ->
        val response = client.get("/normal/v1/${reader.format.path}")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = parseObject(response.bodyAsText()).obj("error")
        assertEquals("unknown_url", error.text("code"))
        assertEquals(howToCall, error.text("message"))
      }
    }

  @Test
  fun `a body that is not a request answers 400 in the format's error shape`() = stubHostTest(folder) {
    val bodies = listOf("[1, 2]", "{}", "not json")
    FormatReader.all.forEach { reader ->
      bodies.forEach { body ->
        val response = client.send("/normal/v1/${reader.format.path}", body)
        assertEquals(HttpStatusCode.BadRequest, response.status, body)
        assertTrue(reader.errorMessage(parseObject(response.bodyAsText())).startsWith("The request body is not valid"))
      }
    }
  }

  @Test
  fun `a stream flag given as the string true is refused`() = stubHostTest(folder) {
    FormatReader.all.forEach { reader ->
      val response = client.send("/normal/v1/${reader.format.path}", """{"model": "m", "stream": "true"}""")
      assertEquals(HttpStatusCode.BadRequest, response.status, reader.format.path)
    }
  }

  @Test
  fun `a null stream flag answers whole`() = stubHostTest(folder) {
    FormatReader.all.forEach { reader ->
      val body = """{"model": "${FormatReader.TEST_MODEL}", "stream": null, """ +
        """"stream_options": {"include_usage": null}}"""
      val response = client.send("/normal/v1/${reader.format.path}", body)
      assertEquals(HttpStatusCode.OK, response.status, reader.format.path)
      reader.readWhole(parseObject(response.bodyAsText()))
    }
  }

  @Test
  fun `every format names the request's model in its reply`() = stubHostTest(folder) {
    val request = buildJsonObject { put("model", "MARKER-MODEL") }.toString()
    FormatReader.all.forEach { reader ->
      val reply = parseObject(client.send("/normal/v1/${reader.format.path}", request).bodyAsText())
      assertEquals("MARKER-MODEL", reply.text("model"), reader.format.path)
    }
  }

  @Test
  fun `the 429 mode says when to retry`() = stubHostTest(folder) {
    FormatReader.all.forEach { reader ->
      val response = client.send("/429/v1/${reader.format.path}", reader.request(streams = false).toString())
      assertEquals(HttpStatusCode.TooManyRequests, response.status)
      assertEquals("1", response.headers[HttpHeaders.RetryAfter], reader.format.path)
    }
  }

  @Test
  fun `a streamed Chat Completions reply sends its usage chunk only when asked`() = stubHostTest(folder) {
    val asked = client.send("/normal/v1/chat/completions", ChatCompletionsReader.request(streams = true).toString())
    assertEquals(1, ChatCompletionsReader.usageChunkCount(parseSse(asked.bodyAsText())))
    val unasked = buildJsonObject {
      put("model", FormatReader.TEST_MODEL)
      put("stream", true)
    }
    val plain = client.send("/normal/v1/chat/completions", unasked.toString())
    assertEquals(0, ChatCompletionsReader.usageChunkCount(parseSse(plain.bodyAsText())))
  }

  @Test
  fun `the record answers only GET and is left out of itself`() = stubHostTest(folder) { record ->
    assertEquals(HttpStatusCode.MethodNotAllowed, client.send(RECORD_PATH, "{}").status)
    assertEquals(HttpStatusCode.MethodNotAllowed, client.head(RECORD_PATH).status)
    assertEquals(HttpStatusCode.OK, client.get(RECORD_PATH).status)
    assertEquals("", record.read())
  }
}

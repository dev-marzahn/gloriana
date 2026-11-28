package dev.marzahn.gloriana.stubhost

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelListTest {
  @TempDir
  lateinit var folder: Path

  private suspend fun ApplicationTestBuilder.list(
    mode: String,
    anthropic: Boolean,
    headers: Map<String, String> = emptyMap(),
  ): HttpResponse = client.get("/$mode/v1/models") {
    if (anthropic) header("anthropic-version", "2023-06-01")
    headers.forEach { (name, value) -> header(name, value) }
  }

  @Test
  fun `an Anthropic request gets a model page with display names`() = stubHostTest(folder) {
    val response = list("normal", anthropic = true)

    assertEquals(HttpStatusCode.OK, response.status)
    val page = parseObject(response.bodyAsText())
    assertEquals(stubModelIds, page.objects("data").map { it.text("id") })
    assertEquals(listOf("Stub Model Large", "Stub Model Small"), page.objects("data").map { it.text("display_name") })
    assertEquals(listOf("model"), page.objects("data").map { it.text("type") }.distinct())
    assertEquals("false", page.textOrNull("has_more"))
  }

  @Test
  fun `a request without anthropic-version gets the OpenAI model list`() = stubHostTest(folder) {
    val list = parseObject(list("normal", anthropic = false).bodyAsText())

    assertEquals("list", list.text("object"))
    assertEquals(stubModelIds, list.objects("data").map { it.text("id") })
    assertEquals(listOf("model"), list.objects("data").map { it.text("object") }.distinct())
  }

  @Test
  fun `empty-list lists nothing and no-list answers 404 in the format's shape`() = stubHostTest(folder) {
    listOf(true, false).forEach { anthropic ->
      assertEquals(emptyList(), parseObject(list("empty-list", anthropic).bodyAsText()).objects("data"))
      val missing = list("no-list", anthropic)
      assertEquals(HttpStatusCode.NotFound, missing.status)
      val error = parseObject(missing.bodyAsText()).obj("error")
      assertEquals(if (anthropic) "not_found_error" else "invalid_request_error", error.text("type"))
    }
  }

  @Test
  fun `a failing mode fails the list with its status and the key echoed where asked`() = stubHostTest(folder) {
    mapOf("401" to 401, "429" to 429, "500" to 500, "stream-error" to 500).forEach { (mode, status) ->
      assertEquals(status, list(mode, anthropic = false).status.value, mode)
    }
    val echoed = list("key-echo", anthropic = true, headers = mapOf("x-api-key" to "MARKER-LIST-1"))
    assertEquals(HttpStatusCode.Unauthorized, echoed.status)
    assertEquals("The key MARKER-LIST-1 is not valid.", AnthropicReader.errorMessage(parseObject(echoed.bodyAsText())))
    val bearer =
      list("key-echo", anthropic = false, headers = mapOf(HttpHeaders.Authorization to "Bearer MARKER-LIST-2"))
    assertEquals("The key MARKER-LIST-2 is not valid.", parseObject(bearer.bodyAsText()).obj("error").text("message"))
  }

  @Test
  fun `the list request is recorded with its query`() = stubHostTest(folder) { record ->
    client.get("/normal/v1/models?limit=1000")

    val line = parseObject(record.read().lines().first(String::isNotBlank))
    assertEquals("GET", line.text("method"))
    assertEquals("/normal/v1/models", line.text("path"))
    assertEquals("limit=1000", line.text("query"))
  }
}

package dev.marzahn.gloriana.stubhost

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestRecordTest {
  @TempDir
  lateinit var folder: Path

  private val clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC)

  @Test
  fun `every request is one JSON line in the file and at the record endpoint`() =
    stubHostTest(folder, clock) { record ->
    client.send("/chat/normal/v1/messages", """{"model":"m"}""", mapOf("x-api-key" to "MARKER-KEY", "X-Custom" to "a"))
    client.send("/summary/500/v1/responses", "not json")
    client.get("/nowhere")

    val served = client.get(RECORD_PATH)
    assertEquals(ndJson, served.contentType()?.withoutParameters())
    assertEquals(Files.readString(record.file), served.bodyAsText())
    val lines = served.bodyAsText().lines().filter(String::isNotEmpty).map(::parseObject)
    assertEquals(
      listOf(
        listOf("2026-01-02T03:04:05Z", "POST", "/chat/normal/v1/messages"),
        listOf("2026-01-02T03:04:05Z", "POST", "/summary/500/v1/responses"),
        listOf("2026-01-02T03:04:05Z", "GET", "/nowhere"),
      ),
      lines.map { listOf(it.text("receivedAt"), it.text("method"), it.text("path")) },
    )
    assertEquals(
      listOf(parseObject("""{"model":"m"}"""), JsonPrimitive("not json"), JsonNull),
      lines.map { it.getValue("body") },
    )
    val headers = lines.first().obj("headers")
    assertEquals(JsonArray(listOf(JsonPrimitive("MARKER-KEY"))), headers.getValue("x-api-key"))
    assertEquals(JsonArray(listOf(JsonPrimitive("a"))), headers.getValue("x-custom"))
  }

  @Test
  fun `a record file that already exists is appended to`() = runBlocking {
    val file = folder.resolve("record.jsonl")
    Files.writeString(file, "{\"earlier\":true}\n")
    RequestRecord.open(file, clock).append("GET", "/normal/v1/models", "limit=1000", emptyMap(), "")
    assertEquals(
      listOf(
        """{"earlier":true}""",
        """{"receivedAt":"2026-01-02T03:04:05Z","method":"GET","path":"/normal/v1/models","query":"limit=1000",""" +
          """"headers":{},"body":null}""",
      ),
      Files.readAllLines(file),
    )
  }

  @Test
  fun `large requests appended while the record is read each land whole on their own line`() = runBlocking {
    val record = RequestRecord.open(folder.resolve("record.jsonl"), clock)
    val count = 48
    val largeHeader = "y".repeat(LARGER_THAN_ONE_WRITE)
    val readsWereWhole = withContext(Dispatchers.Default) {
      val appends = (1..count).map { n ->
        async { record.append("POST", "/n/$n", "", mapOf("x" to listOf(largeHeader)), "{}") }
      }
      val readers = (1..count).map { async { holdsOnlyWholeLines(record.read()) } }
      appends.awaitAll()
      readers.awaitAll()
    }
    assertEquals(List(count) { true }, readsWereWhole, "every read ends between lines and parses")
    val paths = record.read().lines().filter(String::isNotEmpty).map { parseObject(it).text("path") }
    assertEquals((1..count).map { "/n/$it" }.toSet(), paths.toSet())
    assertEquals(count, paths.size)
  }

  @Test
  fun `a record file that can't be written fails to open`() {
    assertFailsWith<IOException> { RequestRecord.open(folder.resolve("missing").resolve("record.jsonl")) }
  }

  private fun holdsOnlyWholeLines(read: String): Boolean =
    read.isEmpty() || read.endsWith("\n") && read.lines().filter(String::isNotEmpty).all(::isJsonObject)

  private fun isJsonObject(line: String): Boolean = runCatching { parseObject(line) }.isSuccess

  private companion object {
    const val LARGER_THAN_ONE_WRITE = 32 * 1024
  }
}

package dev.marzahn.gloriana.stubhost

import dev.marzahn.gloriana.stubhost.PartKind.REASONING
import dev.marzahn.gloriana.stubhost.PartKind.REFUSAL
import dev.marzahn.gloriana.stubhost.PartKind.TEXT
import io.ktor.client.request.preparePost
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readLine
import kotlinx.io.readString
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class EveryModeTest {
  @TempDir
  lateinit var folder: Path

  private sealed interface Expected {
    data class Answer(val received: Received) : Expected

    data class Error(val status: HttpStatusCode, val message: String) : Expected
  }

  private class Case(
    val mode: String,
    val expected: Expected,
    val key: String? = MARKER_KEY,
    val startPause: Duration = Duration.ZERO,
    val eventPause: Duration = Duration.ZERO,
  ) {
    val name = if (key == null) "$mode without a key" else mode
  }

  private class TimedResponse(
    val status: HttpStatusCode,
    val contentType: ContentType?,
    val body: String,
    val toFirstLine: Duration,
    val toEnd: Duration,
  )

  private val plainAnswer = answer(ReceivedTokens(10, 10, 20, 0), ReceivedPart(TEXT, PLAIN))

  private val cases = listOf(
    Case("normal", plainAnswer),
    Case(
      "reasoning",
      answer(
        ReceivedTokens(10, 15, 25, 9),
        ReceivedPart(REASONING, "The user greeted me, so I greet them back."),
        ReceivedPart(TEXT, "Hello again from the stub host."),
      ),
    ),
    Case("status", answer(ReceivedTokens(10, 9, 19, 0), ReceivedPart(TEXT, STATUS))),
    Case("refusal", answer(ReceivedTokens(10, 6, 16, 0), ReceivedPart(REFUSAL, "I can't help with that request."))),
    Case("401", Expected.Error(HttpStatusCode.Unauthorized, "The stub host rejects every key on this path.")),
    Case("429", Expected.Error(HttpStatusCode.TooManyRequests, "The stub host is limiting requests on this path.")),
    Case("500", Expected.Error(HttpStatusCode.InternalServerError, "The stub host fails every request on this path.")),
    Case("key-echo", Expected.Error(HttpStatusCode.Unauthorized, "The key $MARKER_KEY is not valid.")),
    Case("key-echo", Expected.Error(HttpStatusCode.Unauthorized, "No key was sent."), key = null),
    Case("delay-$PAUSE_MS", plainAnswer, startPause = PAUSE_MS.milliseconds),
    Case("slow-$PAUSE_MS", plainAnswer, eventPause = PAUSE_MS.milliseconds),
  )

  @TestFactory
  fun `every format answers in every mode, streamed and whole`(): List<DynamicTest> =
    FormatReader.all.flatMap { reader ->
      cases.flatMap { case ->
        listOf(false, true).map { streams ->
          dynamicTest("${reader.format.path} ${case.name} ${if (streams) "streamed" else "whole"}") {
            run(reader, case, streams)
          }
        }
      }
    }

  private fun run(reader: FormatReader, case: Case, streams: Boolean) =
    stubHostTest(Files.createTempDirectory(folder, "case")) { record ->
      startTheHost()
      val path = "/${case.mode}/v1/${reader.format.path}"
      val headers = listOfNotNull(case.key?.let(reader::keyHeader)).toMap()
      val response = timedPost(path, reader.request(streams).toString(), headers)

      when (val expected = case.expected) {
        is Expected.Answer -> {
          assertEquals(HttpStatusCode.OK, response.status)
          if (streams) {
            assertEquals(ContentType.Text.EventStream, response.contentType)
            assertEquals(reader.asReported(expected.received), reader.readStream(parseSse(response.body)))
          } else {
            assertEquals(ContentType.Application.Json, response.contentType)
            assertEquals(reader.asReported(expected.received), reader.readWhole(parseObject(response.body)))
          }
        }
        is Expected.Error -> {
          assertEquals(expected.status, response.status)
          assertEquals(ContentType.Application.Json, response.contentType)
          assertEquals(expected.message, reader.errorMessage(parseObject(response.body)))
        }
      }
      val gaps = if (streams && case.expected is Expected.Answer) parseSse(response.body).size - 1 else 0
      assertWithin(response.toFirstLine, case.startPause, SLACK, "time to the first line")
      assertWithin(response.toEnd, case.eventPause * gaps, SLACK + GAP_JITTER * gaps, "time from the first line on")
      assertEquals(listOf(path), record.read().lines().filter(String::isNotEmpty).map { parseObject(it).text("path") })
    }

  private suspend fun ApplicationTestBuilder.timedPost(
    path: String,
    body: String,
    headers: Map<String, String>,
  ): TimedResponse {
    val started = TimeSource.Monotonic.markNow()
    return client.preparePost(path) { carry(body, headers) }.execute { response ->
      val channel = response.bodyAsChannel()
      val firstLine = channel.readLine().orEmpty()
      val toFirstLine = started.elapsedNow()
      val rest = channel.readBuffer().readString()
      TimedResponse(
        status = response.status,
        contentType = response.contentType()?.withoutParameters(),
        body = "$firstLine\n$rest",
        toFirstLine = toFirstLine,
        toEnd = started.elapsedNow() - toFirstLine,
      )
    }
  }

  private fun assertWithin(measured: Duration, due: Duration, slack: Duration, what: String) =
    assertTrue(measured >= due && measured < due + slack, "$what: $measured, due $due, slack $slack")

  private companion object {
    const val MARKER_KEY = "MARKER-KEY-every-mode"
    const val PAUSE_MS = 160
    val SLACK = 120.milliseconds
    val GAP_JITTER = 30.milliseconds
    const val PLAIN = "Hello from the stub host. This reply arrives in parts."
    const val STATUS = "The scene goes on.\n\n<details><summary>Status</summary>Health: 10/10\nMood: calm</details>"

    fun answer(tokens: ReceivedTokens, vararg parts: ReceivedPart) = Expected.Answer(Received(parts.toList(), tokens))
  }
}

package dev.marzahn.gloriana.stubhost

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

internal val testModes = Modes(defaultDelay = 1.seconds, defaultSlowPause = 1.seconds)

internal fun stubHostTest(
  folder: Path,
  clock: Clock = Clock.systemUTC(),
  block: suspend ApplicationTestBuilder.(RequestRecord) -> Unit,
) {
  val record = RequestRecord.open(folder.resolve("record.jsonl"), clock)
  testApplication {
    application { stubHost(record, Endpoints(testModes)) }
    block(record)
  }
}

internal suspend fun HttpClient.send(
  path: String,
  body: String,
  headers: Map<String, String> = emptyMap(),
): HttpResponse = post(path) { carry(body, headers) }

internal fun HttpRequestBuilder.carry(body: String, headers: Map<String, String>) {
  headers.forEach { (name, value) -> header(name, value) }
  setBody(body)
}

internal suspend fun ApplicationTestBuilder.startTheHost() {
  client.get(RECORD_PATH)
}

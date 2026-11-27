package dev.marzahn.gloriana.stubhost

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val LOOPBACK = "127.0.0.1"
private const val ANY_FREE_PORT = 0

class RunningStubHost internal constructor(
  private val server: EmbeddedServer<*, *>,
  val address: String,
  private val record: RequestRecord,
) : AutoCloseable {
  fun baseUrl(mode: String): String = "$address/$mode/v1"

  suspend fun recorded(): String = record.read()

  override fun close() = server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
}

suspend fun startStubHost(
  recordFile: Path,
  delay: Duration = 1.seconds,
  slowPause: Duration = 1.seconds,
): RunningStubHost = startStubHost(RequestRecord.open(recordFile), ANY_FREE_PORT, Endpoints(Modes(delay, slowPause)))

internal suspend fun startStubHost(record: RequestRecord, port: Int, endpoints: Endpoints): RunningStubHost {
  val server = embeddedServer(CIO, port = port, host = LOOPBACK) { stubHost(record, endpoints) }.startSuspend()
  val address = "http://$LOOPBACK:${server.engine.resolvedConnectors().first().port}"
  return RunningStubHost(server, address, record)
}

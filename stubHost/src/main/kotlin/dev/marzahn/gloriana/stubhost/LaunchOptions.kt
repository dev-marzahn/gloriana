package dev.marzahn.gloriana.stubhost

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal data class LaunchOptions(
  val port: Int = ANY_PORT,
  val recordFile: Path? = null,
  val delay: Duration = DEFAULT_DELAY,
  val slowPause: Duration = DEFAULT_SLOW_PAUSE,
) {
  companion object {
    private const val ANY_PORT = 0
    private const val MAX_PORT = 65_535
    private const val PORT = "--port"
    private const val RECORD = "--record"
    private const val DELAY = "--delay-ms"
    private const val SLOW = "--slow-ms"
    private val options = setOf(PORT, RECORD, DELAY, SLOW)
    val helpFlags = setOf("--help", "-h")
    val DEFAULT_DELAY = 60.seconds
    val DEFAULT_SLOW_PAUSE = 1.seconds

    val help: String = """
      Run with: ./gradlew :stubHost:run --args="[options]"
        $PORT <port>    the port, $ANY_PORT to $MAX_PORT (default $ANY_PORT: any free port)
        $RECORD <file>  the record file, appended to (default: a new temporary file)
        $DELAY <ms>   the delay mode's pause before it answers (default ${DEFAULT_DELAY.inWholeMilliseconds})
        $SLOW <ms>    the slow mode's pause between stream events (default ${DEFAULT_SLOW_PAUSE.inWholeMilliseconds})
        ${helpFlags.joinToString(", ")}      this help
    """.trimIndent()

    fun parse(args: List<String>): LaunchOptions {
      val values = mutableMapOf<String, String>()
      args.chunked(2).forEach { pair ->
        val name = pair.first()
        require(name in options) { "Unknown option $name." }
        require(pair.size == 2) { "$name needs a value." }
        require(values.put(name, pair.last()) == null) { "$name is given twice." }
      }
      return LaunchOptions(
        port = values[PORT]?.let(::port) ?: ANY_PORT,
        recordFile = values[RECORD]?.let { Paths.get(it) },
        delay = values[DELAY]?.let { millis(DELAY, it) } ?: DEFAULT_DELAY,
        slowPause = values[SLOW]?.let { millis(SLOW, it) } ?: DEFAULT_SLOW_PAUSE,
      )
    }

    private fun port(value: String): Int = requireNotNull(value.toIntOrNull()?.takeIf { it in ANY_PORT..MAX_PORT }) {
      "$PORT takes a port from $ANY_PORT to $MAX_PORT."
    }

    private fun millis(name: String, value: String): Duration =
      requireNotNull(value.toLongOrNull()?.takeIf { it >= 0 }) { "$name takes milliseconds, 0 or more." }.milliseconds
  }
}

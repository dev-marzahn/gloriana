package dev.marzahn.gloriana.stubhost

import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LaunchOptionsTest {
  @TempDir
  lateinit var folder: Path

  @Test
  fun `with no options it takes any free port, a temporary record, a minute's delay and a second's slow pause`() {
    assertEquals(
      LaunchOptions(port = 0, recordFile = null, delay = 1.minutes, slowPause = 1.seconds),
      LaunchOptions.parse(emptyList()),
    )
  }

  @Test
  fun `each option sets its value`() {
    assertEquals(
      LaunchOptions(port = 8787, recordFile = Paths.get("r.jsonl"), delay = 1500.milliseconds, slowPause = 0.seconds),
      LaunchOptions.parse(listOf("--record", "r.jsonl", "--port", "8787", "--delay-ms", "1500", "--slow-ms", "0")),
    )
  }

  @Test
  fun `a bad option stops the launch with its reason and the help`() {
    mapOf(
      listOf("--host", "0.0.0.0") to "Unknown option --host.",
      listOf("--port") to "--port needs a value.",
      listOf("--port", "eighty") to "--port takes a port from 0 to 65535.",
      listOf("--port", "65536") to "--port takes a port from 0 to 65535.",
      listOf("--port", "-1") to "--port takes a port from 0 to 65535.",
      listOf("--delay-ms", "-5") to "--delay-ms takes milliseconds, 0 or more.",
      listOf("--slow-ms", "soon") to "--slow-ms takes milliseconds, 0 or more.",
      listOf("--port", "1", "--port", "2") to "--port is given twice.",
    ).forEach { (args, reason) ->
      val exit = Launch.Exit(Launch.ExitReason.INVALID_OPTIONS, "$reason\n\n${LaunchOptions.help}")
      assertEquals(exit, Launch.from(args), args.toString())
    }
    assertEquals(2, Launch.ExitReason.INVALID_OPTIONS.exitCode)
  }

  @Test
  fun `a help flag anywhere shows the help`() {
    listOf(listOf("--help"), listOf("--port", "1", "-h")).forEach {
      assertEquals(Launch.ShowHelp, Launch.from(it))
    }
  }

  @Test
  fun `a record file that can't be written stops the launch with the plain reason`() {
    val missingFolder = folder.resolve("missing").resolve("record.jsonl")
    val readOnly = Files.createDirectory(folder.resolve("read-only")).also { it.toFile().setWritable(false) }
    mapOf(
      missingFolder to "its folder doesn't exist",
      folder to "it is a folder",
      readOnly.resolve("record.jsonl") to "permission denied",
    ).forEach { (file, reason) ->
      assertEquals(
        Launch.Exit(Launch.ExitReason.RECORD_UNWRITABLE, "Can't write ${file.toAbsolutePath()}: $reason."),
        Launch.from(listOf("--record", file.toString())),
      )
    }
    assertEquals(1, Launch.ExitReason.RECORD_UNWRITABLE.exitCode)
  }

  @Test
  fun `a temporary record file that can't be made stops the launch too`() {
    assertEquals(
      Launch.Exit(Launch.ExitReason.RECORD_UNWRITABLE, "Can't write a temporary record file: no space left."),
      Launch.from(emptyList(), temporaryRecord = { throw IOException("no space left") }),
    )
  }

  @Test
  fun `with no record file it opens a temporary one`() {
    val file = folder.resolve("temporary.jsonl")
    val launch = assertIs<Launch.Start>(Launch.from(emptyList(), temporaryRecord = { file }))
    assertEquals(file, launch.record.file)
  }
}

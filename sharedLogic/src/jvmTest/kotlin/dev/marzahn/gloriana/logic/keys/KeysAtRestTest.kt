package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.characters.CharacterLibrary
import dev.marzahn.gloriana.logic.characters.ImporterCorpus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val MARKER_KEY = "sk-marker-4f1c2d9e7a6b"
private const val MARKER_HEADER_VALUE = "hv-marker-8b3e5f0a1c2d"

class KeysAtRestTest {
  @TempDir
  lateinit var library: Path

  private val connection = Uuid.random()

  @Test
  fun noFileOrLogLineHoldsAKeyOrHeaderValueInPlainText() = runTest {
    val keychainVaults = Vaults(library)
    val output = capturedOutput {
      CharacterLibrary(library).use { it.importedCharacter(ImporterCorpus.charx) }
      val keychainVault = keychainVaults.start()
      sealMarkers(keychainVault)
      keychainVault.erase(Uuid.random())
      val passphrase = Vaults(library.resolve("passphrase")).apply { keychain.noKeychain = true }.start()
      passphrase.setPassphrase("correct horse".passphrase())
      sealMarkers(passphrase)
    }

    assertTrue(Files.isRegularFile(library.resolve("keys").resolve("keyset-device.json")))
    assertEquals(emptyList(), filesHoldingAMarker())
    assertFalse(output.holdsAMarker(), "the log output holds a marker")
  }

  @Test
  fun theSearchFindsAMarkerInEveryEncodingItChecks() {
    Files.createDirectories(library.resolve("nested"))
    Files.write(library.resolve("plain.txt"), MARKER_KEY.toByteArray(Charsets.UTF_8))
    Files.write(library.resolve("nested").resolve("wide.bin"), MARKER_HEADER_VALUE.toByteArray(Charsets.UTF_16LE))

    assertEquals(2, filesHoldingAMarker().size)
  }

  @Test
  fun theCaptureSeesStandardOutputAndJavaLogging() {
    val output = capturedOutput {
      println(MARKER_KEY)
      java.util.logging.Logger.getLogger("com.google.crypto.tink").fine(MARKER_HEADER_VALUE)
    }

    assertTrue(output.decodeToString().contains(MARKER_KEY))
    assertTrue(output.decodeToString().contains(MARKER_HEADER_VALUE))
  }

  private suspend fun sealMarkers(vault: KeyVault) {
    vault.sealed(apiKey(connection), MARKER_KEY)
    vault.sealed(header(connection, "X-Marker"), MARKER_HEADER_VALUE)
  }

  private fun filesHoldingAMarker(): List<Path> = library.toFile().walkTopDown()
    .map { it.toPath() }
    .filter { it.isRegularFile() && it.readBytes().holdsAMarker() }
    .toList()

  private inline fun capturedOutput(action: () -> Unit): ByteArray {
    val captured = ByteArrayOutputStream()
    val out = System.out
    val err = System.err
    val root = LogManager.getLogManager().getLogger("")
    val logged = CapturingHandler(captured)
    val level = root.level
    PrintStream(captured, true).use { stream ->
      System.setOut(stream)
      System.setErr(stream)
      root.level = Level.ALL
      root.addHandler(logged)
      try {
        action()
      } finally {
        root.removeHandler(logged)
        root.level = level
        System.setOut(out)
        System.setErr(err)
      }
    }
    return captured.toByteArray()
  }

  private fun ByteArray.holdsAMarker(): Boolean = listOf(MARKER_KEY, MARKER_HEADER_VALUE)
    .flatMap { marker -> listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).map(marker::toByteArray) }
    .any { marker -> indexOf(marker) >= 0 }

  private fun ByteArray.indexOf(part: ByteArray): Int =
    (0..size - part.size).firstOrNull { start -> part.indices.all { this[start + it] == part[it] } } ?: -1

  private class CapturingHandler(private val target: ByteArrayOutputStream) : Handler() {
    init {
      level = Level.ALL
      formatter = SimpleFormatter()
    }

    override fun publish(record: LogRecord) = synchronized(target) {
      target.write(formatter.format(record).encodeToByteArray())
    }

    override fun flush() = Unit

    override fun close() = Unit
  }
}

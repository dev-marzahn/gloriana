package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.characters.CharacterLibrary
import dev.marzahn.gloriana.logic.characters.ImporterCorpus
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDeleteResult
import dev.marzahn.gloriana.logic.connection.ConnectionLibrary
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.connection.entered
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
import kotlin.io.encoding.Base64
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MARKER_KEY = "sk-marker-4f1c2d9e7a6b"
private const val MARKER_HEADER_VALUE = "hv-marker-8b3e5f0a1c2d"
private const val PASSPHRASE = "correct horse"

private class Ciphertext(val slot: SecretSlot, val secret: SealedSecret) {
  val storedForms: List<ByteArray> = listOf(secret.bytes, Base64.encode(secret.bytes).encodeToByteArray())
}

class KeysAtRestTest {
  @TempDir
  lateinit var library: Path

  @Test
  fun underTheKeychainNoFileOrLogHoldsTheValuesAndADeleteLeavesNoCiphertextThatOpens() = runTest {
    checkKeysAtRest(Vaults(library), firstUse = {}, afterRestart = {})

    assertTrue(Files.isRegularFile(library.resolve("keys").resolve("keyset-device.json")))
  }

  @Test
  fun underAPassphraseNoFileOrLogHoldsTheValuesAndADeleteLeavesNoCiphertextThatOpens() = runTest {
    checkKeysAtRest(
      Vaults(library).apply { keychain.noKeychain = true },
      firstUse = { it.setPassphrase(PASSPHRASE.passphrase()) },
      afterRestart = { it.unlock(PASSPHRASE.passphrase()) },
    )

    assertTrue(Files.isRegularFile(library.resolve("keys").resolve("keyset-passphrase.json")))
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

  private suspend fun checkKeysAtRest(
    vaults: Vaults,
    firstUse: suspend (KeyVault) -> Unit,
    afterRestart: suspend (KeyVault) -> Unit,
  ) {
    val output = capturedOutput {
      CharacterLibrary(library).use { it.importedCharacter(ImporterCorpus.charx) }
      ConnectionLibrary(library, vaults = vaults).use { connections ->
        firstUse(connections.vault)
        val saved = connections.created(
          draft(
            key = KeyInput.Entered(MARKER_KEY),
            headers = listOf(HeaderDraft("X-Marker", entered(MARKER_HEADER_VALUE))),
          ),
        )
        assertEquals(emptyList(), filesHoldingAMarker())
        val ciphertexts = saved.ciphertexts()
        assertTrue(ciphertexts.all { filesHolding(it).isNotEmpty() }, "the open database holds each ciphertext")

        assertEquals(ConnectionDeleteResult.Deleted, connections.repository.delete(saved.id))

        assertFalse(connections.dataKeyFileHolds(saved.id))
        val restarted = vaults.start().also { afterRestart(it) }
        ciphertexts.forEach { ciphertext ->
          assertEquals(OpenResult.Missing, connections.vault.open(ciphertext.slot, ciphertext.secret))
          assertEquals(OpenResult.Missing, restarted.open(ciphertext.slot, ciphertext.secret))
        }
        assertEquals(emptyList(), filesHoldingAMarker())
      }
    }
    assertFalse(output.holdsAMarker(), "the log output holds a marker")
  }

  private fun Connection.ciphertexts(): List<Ciphertext> =
    listOf(Ciphertext(SecretSlot.ApiKey(id), checkNotNull(key))) +
      headers.map { Ciphertext(header(id, it.name), it.value) }

  private fun filesHolding(ciphertext: Ciphertext): List<Path> =
    everyFile().filter { file -> file.readBytes().let { bytes -> ciphertext.storedForms.any { bytes.holds(it) } } }

  private fun filesHoldingAMarker(): List<Path> = everyFile().filter { it.readBytes().holdsAMarker() }

  private fun everyFile(): List<Path> =
    library.toFile().walkTopDown().map { it.toPath() }.filter { it.isRegularFile() }.toList()

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
    .any { holds(it) }

  private fun ByteArray.holds(part: ByteArray): Boolean =
    String(this, Charsets.ISO_8859_1).contains(String(part, Charsets.ISO_8859_1))

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

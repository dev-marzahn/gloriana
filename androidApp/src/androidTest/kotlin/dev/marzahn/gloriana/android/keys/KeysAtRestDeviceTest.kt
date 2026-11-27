package dev.marzahn.gloriana.android.keys

import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDeleteResult
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.logic.keys.keyVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val MARKER_KEY = "sk-marker-device-6d2a9c41"
private const val MARKER_HEADER_VALUE = "hv-marker-device-1e7b3f08"

private class Ciphertext(val slot: SecretSlot, val secret: SealedSecret) {
  val storedForms: List<ByteArray> = listOf(secret.bytes, Base64.encode(secret.bytes).encodeToByteArray())
}

class KeysAtRestDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val koin = GlobalContext.get()
  private val repository = koin.get<ConnectionRepository>()
  private val vault = koin.get<KeyVault>()

  @Test
  fun noAppFileHoldsTheValuesAndADeleteLeavesNoCiphertextThatOpens() = runBlocking {
    val saved = savedWithMarkers()
    assertEquals(emptyList(), filesHoldingAMarker())
    val ciphertexts = saved.ciphertexts()
    assertTrue(ciphertexts.all { filesHolding(it).isNotEmpty() }, "the open database holds each ciphertext")

    assertEquals(ConnectionDeleteResult.Deleted, repository.delete(saved.id))

    val restarted = keyVault(
      LibraryDirectory { context.filesDir.toPath() },
      KeystoreKeyStore(AndroidKeystoreKeys()),
      Faults.None,
      stored = { repository.connections().first().map(Connection::id).toSet() },
    )
    restarted.prepareIfStored()
    assertTrue(restarted.findings.first().isMissing(SecretSlot.ApiKey(saved.id)), "the data key is gone")
    ciphertexts.forEach { ciphertext ->
      assertEquals(OpenResult.Missing, vault.open(ciphertext.slot, ciphertext.secret))
      assertEquals(OpenResult.Missing, restarted.open(ciphertext.slot, ciphertext.secret))
    }
    assertEquals(emptyList(), filesHoldingAMarker())
    assertFalse(appLog().holdsAMarker(), "the app's log holds a marker")
  }

  @Test
  fun theLogSearchSeesTheAppsOwnLog() {
    Log.i("KeysAtRestDeviceTest", "log-probe-3c8e1a")

    assertTrue("log-probe-3c8e1a" in appLog().decodeToString())
  }

  private fun appLog(): ByteArray = InstrumentationRegistry.getInstrumentation().uiAutomation
    .executeShellCommand("logcat -d --pid=${Process.myPid()}")
    .let { ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }

  private suspend fun savedWithMarkers(): Connection {
    val fields = ConnectionFields(
      name = "Keys at rest ${Uuid.random()}",
      format = ApiFormat.OpenAiChatCompletions,
      baseUrl = "https://api.example.com/v1",
      model = "model-1",
    )
    val draft = ConnectionDraft(
      fields,
      KeyInput.Entered(MARKER_KEY),
      listOf(HeaderDraft("X-Marker", HeaderValueInput.Entered(MARKER_HEADER_VALUE))),
    )
    val id = assertIs<ConnectionSaveResult.Saved>(repository.create(draft)).id
    return checkNotNull(repository.connection(id).first())
  }

  private fun Connection.ciphertexts(): List<Ciphertext> =
    listOf(Ciphertext(SecretSlot.ApiKey(id), checkNotNull(key))) +
      headers.map { Ciphertext(SecretSlot.HeaderValue(id, HeaderName.of(it.name)), it.value) }

  private fun filesHolding(ciphertext: Ciphertext): List<File> =
    everyFile().filter { file -> file.readBytes().let { bytes -> ciphertext.storedForms.any { bytes.holds(it) } } }

  private fun filesHoldingAMarker(): List<File> = everyFile().filter { it.readBytes().holdsAMarker() }

  private fun everyFile(): List<File> = context.dataDir.walkTopDown().filter { it.isFile }.toList()

  private fun ByteArray.holdsAMarker(): Boolean = listOf(MARKER_KEY, MARKER_HEADER_VALUE)
    .flatMap { marker -> listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).map(marker::toByteArray) }
    .any { holds(it) }

  private fun ByteArray.holds(part: ByteArray): Boolean =
    String(this, Charsets.ISO_8859_1).contains(String(part, Charsets.ISO_8859_1))
}

package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.KeysetHandle
import dev.marzahn.gloriana.logic.storage.createDirectoriesDurably
import dev.marzahn.gloriana.logic.storage.writeAtomically
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64

private const val LINE_BREAK = '\n'
private const val DEVICE_KEYSET_FILE = "keyset-device.json"
private const val PASSPHRASE_KEYSET_FILE = "keyset-passphrase.json"

@Serializable
internal sealed interface KeysetProtection {
  @Serializable
  @SerialName("device")
  data class Device(val key: String) : KeysetProtection

  @Serializable
  @SerialName("passphrase")
  data class Passphrase(val cost: Argon2idCost, val salt: String) : KeysetProtection {
    val isAccepted: Boolean
      get() = cost.isAccepted && saltBytes()?.size == SALT_BYTES

    fun saltBytes(): ByteArray? = decodedOrNull { Base64.getDecoder().decode(salt) }
  }
}

internal enum class KeysetKind(val fileName: String) {
  Device(DEVICE_KEYSET_FILE),
  Passphrase(PASSPHRASE_KEYSET_FILE),
}

internal sealed interface StoredKeyset {
  class Device(val keyName: String, private val header: ByteArray, private val body: ByteArray) : StoredKeyset {
    fun open(wrapping: WrappingKey): KeysetOpening = Keysets.open(body, wrapping, header)
  }

  class Passphrase(
    val protection: KeysetProtection.Passphrase,
    private val header: ByteArray,
    private val body: ByteArray,
  ) : StoredKeyset {
    fun open(passphrase: CharArray, derivation: KeyDerivation): KeysetHandle? =
      when (val opening = Keysets.open(body, protection.wrapping(passphrase, derivation), header)) {
        is KeysetOpening.Opened -> opening.keyset
        KeysetOpening.WrongKey, KeysetOpening.Lost -> null
      }
  }

  data class Damaged(val kind: KeysetKind) : StoredKeyset
}

private inline fun <T> decodedOrNull(decode: () -> T): T? = try {
  decode()
} catch (_: IllegalArgumentException) {
  null
}

internal class KeysetFiles(private val directory: Path) {
  fun storedKind(): KeysetKind? = KeysetKind.entries.reversed().firstOrNull { isStored(directory.resolve(it.fileName)) }

  private fun isStored(file: Path): Boolean = try {
    Files.readAttributes(file, BasicFileAttributes::class.java).isRegularFile
  } catch (_: NoSuchFileException) {
    false
  }

  fun read(): StoredKeyset? =
    storedKind()?.let { kind -> parse(kind, Files.readAllBytes(directory.resolve(kind.fileName)).decodeToString()) }

  fun write(protection: KeysetProtection, keyset: KeysetHandle, wrapping: WrappingKey, staged: () -> Unit = {}) {
    val header = Json.encodeToString(protection)
    val body = Keysets.seal(keyset, wrapping, header.encodeToByteArray())
    val text = header + LINE_BREAK + Base64.getEncoder().encodeToString(body) + LINE_BREAK
    directory.createDirectoriesDurably()
    directory.resolve(protection.kind.fileName).writeAtomically(staged) { it.write(text.encodeToByteArray()) }
  }

  private fun parse(kind: KeysetKind, text: String): StoredKeyset {
    val header = text.substringBefore(LINE_BREAK)
    val protection = decodedOrNull { Json.decodeFromString<KeysetProtection>(header) }
    val body = decodedOrNull { Base64.getDecoder().decode(text.substringAfter(LINE_BREAK, "").trim()) }
      ?.takeIf { it.isNotEmpty() }
    return when {
      body == null || protection?.kind != kind -> StoredKeyset.Damaged(kind)
      protection is KeysetProtection.Device -> StoredKeyset.Device(protection.key, header.encodeToByteArray(), body)
      protection is KeysetProtection.Passphrase && protection.isAccepted ->
        StoredKeyset.Passphrase(protection, header.encodeToByteArray(), body)
      else -> StoredKeyset.Damaged(kind)
    }
  }
}

private val KeysetProtection.kind: KeysetKind
  get() = when (this) {
    is KeysetProtection.Device -> KeysetKind.Device
    is KeysetProtection.Passphrase -> KeysetKind.Passphrase
  }

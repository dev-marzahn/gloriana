package dev.marzahn.gloriana.logic.keys

import com.google.crypto.tink.Aead
import com.google.crypto.tink.Configuration
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKey
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.subtle.Random
import com.google.crypto.tink.util.SecretBytes
import java.security.GeneralSecurityException

internal const val AES_256_KEY_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BYTES = 16

internal object Keysets {
  private val configuration: Configuration

  init {
    AeadConfig.register()
    configuration = RegistryConfiguration.get()
  }

  fun newAes256Gcm(): KeysetHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM, configuration)

  fun aead(keyset: KeysetHandle): Aead = keyset.getPrimitive(configuration, Aead::class.java)

  fun randomBytes(size: Int): ByteArray = Random.randBytes(size)

  fun seal(keyset: KeysetHandle, wrapping: WrappingKey, associatedData: ByteArray): ByteArray {
    val plain = TinkProtoKeysetFormat.serializeKeyset(keyset, InsecureSecretKeyAccess.get(), configuration)
    return try {
      wrapping.seal(plain, associatedData)
    } finally {
      plain.fill(0)
    }
  }

  fun open(sealed: ByteArray, wrapping: WrappingKey, associatedData: ByteArray): KeysetOpening =
    when (val unwrapped = wrapping.open(sealed, associatedData)) {
      is Unwrapped.Opened -> parse(unwrapped.bytes)
      Unwrapped.WrongKey -> KeysetOpening.WrongKey
      Unwrapped.Lost -> KeysetOpening.Lost
    }

  private fun parse(plain: ByteArray): KeysetOpening = try {
    KeysetOpening.Opened(TinkProtoKeysetFormat.parseKeyset(plain, InsecureSecretKeyAccess.get(), configuration))
  } catch (_: GeneralSecurityException) {
    KeysetOpening.WrongKey
  } finally {
    plain.fill(0)
  }

  fun wrap(keyset: KeysetHandle, wrapping: Aead, associatedData: ByteArray): ByteArray =
    TinkProtoKeysetFormat.serializeEncryptedKeyset(keyset, wrapping, associatedData, configuration)

  fun unwrap(wrapped: ByteArray, wrapping: Aead, associatedData: ByteArray): KeysetHandle? = try {
    TinkProtoKeysetFormat.parseEncryptedKeyset(wrapped, wrapping, associatedData, configuration)
  } catch (_: GeneralSecurityException) {
    null
  }
}

internal class AesGcmWrappingKey(rawKey: ByteArray) : WrappingKey {
  private val aead: Aead = Keysets.aead(
    KeysetHandle.newBuilder()
      .addEntry(KeysetHandle.importKey(aesGcmKey(rawKey)).withRandomId().makePrimary())
      .build(),
  )

  override fun seal(plain: ByteArray, associatedData: ByteArray): ByteArray = aead.encrypt(plain, associatedData)

  override fun open(sealed: ByteArray, associatedData: ByteArray): Unwrapped = try {
    Unwrapped.Opened(aead.decrypt(sealed, associatedData))
  } catch (_: GeneralSecurityException) {
    Unwrapped.WrongKey
  }
}

internal sealed interface KeysetOpening {
  class Opened(val keyset: KeysetHandle) : KeysetOpening

  data object WrongKey : KeysetOpening

  data object Lost : KeysetOpening
}

private fun aesGcmKey(rawKey: ByteArray): AesGcmKey = AesGcmKey.builder()
  .setParameters(
    AesGcmParameters.builder()
      .setKeySizeBytes(AES_256_KEY_BYTES)
      .setIvSizeBytes(GCM_IV_BYTES)
      .setTagSizeBytes(GCM_TAG_BYTES)
      .setVariant(AesGcmParameters.Variant.NO_PREFIX)
      .build(),
  )
  .setKeyBytes(SecretBytes.copyFrom(rawKey, InsecureSecretKeyAccess.get()))
  .build()

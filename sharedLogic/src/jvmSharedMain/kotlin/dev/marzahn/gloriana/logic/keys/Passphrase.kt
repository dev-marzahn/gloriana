package dev.marzahn.gloriana.logic.keys

import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.IOException
import java.util.Base64

internal const val SALT_BYTES = 16
private const val KIB_PER_LANE_MINIMUM = 8
private val MEMORY_KIB = 8..64 * 1024
private val ITERATIONS = 1..3
private val LANES = 1..1

@Serializable
data class Argon2idCost(val memoryKib: Int, val iterations: Int, val lanes: Int) {
  val isAccepted: Boolean
    get() = memoryKib in MEMORY_KIB && iterations in ITERATIONS && lanes in LANES &&
      memoryKib >= KIB_PER_LANE_MINIMUM * lanes

  companion object {
    val Default = Argon2idCost(memoryKib = 64 * 1024, iterations = 3, lanes = 1)
  }
}

internal fun newPassphraseProtection(cost: Argon2idCost) = KeysetProtection.Passphrase(
  cost = cost,
  salt = Base64.getEncoder().encodeToString(Keysets.randomBytes(SALT_BYTES)),
)

internal fun interface KeyDerivation {
  fun derive(parameters: Argon2Parameters, passphrase: CharArray, key: ByteArray)

  companion object {
    val Argon2id = KeyDerivation { parameters, passphrase, key ->
      Argon2BytesGenerator().apply { init(parameters) }.generateBytes(passphrase, key)
    }
  }
}

internal fun KeysetProtection.Passphrase.wrapping(passphrase: CharArray, derivation: KeyDerivation): WrappingKey {
  val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
    .withMemoryAsKB(cost.memoryKib)
    .withIterations(cost.iterations)
    .withParallelism(cost.lanes)
    .withSalt(checkNotNull(saltBytes()))
    .build()
  val key = ByteArray(AES_256_KEY_BYTES)
  return try {
    derivation.deriveOrFail(parameters, passphrase, key)
    AesGcmWrappingKey(key)
  } finally {
    key.fill(0)
  }
}

internal class DerivationOutOfMemory(cause: OutOfMemoryError) : IOException(cause)

private fun KeyDerivation.deriveOrFail(parameters: Argon2Parameters, passphrase: CharArray, key: ByteArray) {
  try {
    derive(parameters, passphrase, key)
  } catch (outOfMemory: OutOfMemoryError) {
    throw DerivationOutOfMemory(outOfMemory)
  }
}

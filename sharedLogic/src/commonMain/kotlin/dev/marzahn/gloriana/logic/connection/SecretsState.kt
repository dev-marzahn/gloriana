package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyFindings
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.logic.keys.VaultState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

enum class HeldSecrets {
  Key,
  HeaderValues,
  KeyAndHeaderValues,
  ;

  companion object {
    fun of(key: Boolean, headers: Boolean): HeldSecrets? = when {
      key && headers -> KeyAndHeaderValues
      key -> Key
      headers -> HeaderValues
      else -> null
    }
  }
}

sealed interface SecretsState {
  data object Usable : SecretsState

  data class Missing(val key: Boolean, val headers: Set<HeaderName>, val held: HeldSecrets) : SecretsState

  data class KeychainUnavailable(val held: HeldSecrets) : SecretsState

  data class Locked(val held: HeldSecrets) : SecretsState

  data class Unreadable(val held: HeldSecrets) : SecretsState
}

val Connection.heldSecrets: HeldSecrets?
  get() = HeldSecrets.of(key != null, headers.isNotEmpty())

fun Connection.secretsState(vault: VaultState, findings: KeyFindings): SecretsState {
  val held = heldSecrets ?: return SecretsState.Usable
  return when {
    vault == VaultState.Unavailable -> SecretsState.KeychainUnavailable(held)
    vault == VaultState.Locked || vault == VaultState.PassphraseDamaged -> SecretsState.Locked(held)
    vault.needsStartingOver -> SecretsState.Unreadable(held)
    else -> missingSecrets(findings)
  }
}

private fun Connection.missingSecrets(findings: KeyFindings): SecretsState {
  val keyMissing = key != null && findings.isMissing(SecretSlot.ApiKey(id))
  val headersMissing = headers.map { HeaderName.of(it.name) }
    .filterTo(mutableSetOf()) { findings.isMissing(SecretSlot.HeaderValue(id, it)) }
  val held = HeldSecrets.of(keyMissing, headersMissing.isNotEmpty()) ?: return SecretsState.Usable
  return SecretsState.Missing(keyMissing, headersMissing, held)
}

fun KeyFindings.countHolding(connections: List<Connection>): Int =
  connections.count { connection -> connection.storedSecrets().any { !isMissing(it.slot) } }

fun KeyVault.secretChecks(connections: Flow<List<Connection>>): Flow<Unit> =
  combine(connections, state) { list, vault -> HeldValues(list.flatMap { it.storedSecrets() }, vault.opensValues) }
    .distinctUntilChanged()
    .filter { it.vaultOpen }
    .map { held -> held.values.forEach { open(it.slot, it.secret) } }

private val VaultState.opensValues: Boolean
  get() = this == VaultState.Unlocked || this == VaultState.PassphraseUnlocked

private class HeldValues(val values: List<SecretSource.Stored>, val vaultOpen: Boolean) {
  private val identity = values.map { it.slot to it.secret.bytes.toList() } to vaultOpen

  override fun equals(other: Any?) = other is HeldValues && other.identity == identity

  override fun hashCode() = identity.hashCode()
}

private fun Connection.storedSecrets(): List<SecretSource.Stored> {
  val sources = secretSources()
  return (listOfNotNull(sources.key) + sources.headers.map { it.source }).filterIsInstance<SecretSource.Stored>()
}

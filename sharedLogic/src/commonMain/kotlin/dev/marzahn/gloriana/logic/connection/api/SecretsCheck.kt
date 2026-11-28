package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.secretSources
import dev.marzahn.gloriana.logic.keys.KeyVault

internal class SecretsCheck(private val vault: KeyVault) {
  suspend fun refusal(connection: Connection): ModelApiError? =
    (vault.openSecrets(connection.secretSources()) as? SecretsOpening.Refused)?.error
}

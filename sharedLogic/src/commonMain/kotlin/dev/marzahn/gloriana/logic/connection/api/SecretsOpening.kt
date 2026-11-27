package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.SecretSource
import dev.marzahn.gloriana.logic.connection.SecretSources
import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.OpenResult

internal sealed interface SecretsOpening {
  class Opened(val key: String?, val headers: List<HostHeader>) : SecretsOpening

  class Refused(val error: ModelApiError) : SecretsOpening
}

internal suspend fun KeyVault.openSecrets(sources: SecretSources): SecretsOpening = run opening@{
  val key = sources.key?.let { open(it).valueOr { error -> return@opening SecretsOpening.Refused(error) } }
  val headers = sources.headers.map { header ->
    val value = open(header.source).valueOr { error -> return@opening SecretsOpening.Refused(error) }
    HostHeader(HeaderName.of(header.name), value)
  }
  SecretsOpening.Opened(key, headers)
}

private suspend fun KeyVault.open(source: SecretSource): OpenResult = when (source) {
  is SecretSource.Typed -> OpenResult.Opened(source.value)
  is SecretSource.Stored -> open(source.slot, source.secret)
}

private inline fun OpenResult.valueOr(refuse: (ModelApiError) -> Nothing): String = when (this) {
  is OpenResult.Opened -> value
  OpenResult.Missing -> refuse(ModelApiError.KeysMissing)
  OpenResult.Locked -> refuse(ModelApiError.KeysLocked)
  OpenResult.Unavailable -> refuse(ModelApiError.KeysUnavailable)
  OpenResult.Unreadable -> refuse(ModelApiError.KeysUnreadable)
}

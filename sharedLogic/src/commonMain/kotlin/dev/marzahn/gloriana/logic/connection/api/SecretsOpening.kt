package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.HeldSecrets
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
  val refuse = { error: ModelApiError -> SecretsOpening.Refused(error) }
  val key = sources.key?.let { source ->
    open(source, sources) { error -> return@opening refuse(error) }
  }
  val headers = sources.headers.map { header ->
    HostHeader(HeaderName.of(header.name), open(header.source, sources) { error -> return@opening refuse(error) })
  }
  SecretsOpening.Opened(key, headers)
}

private suspend inline fun KeyVault.open(
  source: SecretSource,
  sources: SecretSources,
  refuse: (ModelApiError) -> Nothing,
): String = when (source) {
  is SecretSource.Typed -> source.value
  is SecretSource.Stored -> when (val result = open(source.slot, source.secret)) {
    is OpenResult.Opened -> result.value
    OpenResult.Missing -> refuse(ModelApiError.KeysMissing(sources.storedHeld()))
    OpenResult.Locked -> refuse(ModelApiError.KeysLocked)
    OpenResult.Unavailable -> refuse(ModelApiError.KeysUnavailable(sources.storedHeld()))
    OpenResult.Unreadable -> refuse(ModelApiError.KeysUnreadable(sources.storedHeld()))
  }
}

private fun SecretSources.storedHeld(): HeldSecrets {
  val keyStored = key is SecretSource.Stored
  val headersStored = headers.any { it.source is SecretSource.Stored }
  return when {
    keyStored && headersStored -> HeldSecrets.KeyAndHeaderValues
    keyStored -> HeldSecrets.Key
    else -> HeldSecrets.HeaderValues
  }
}

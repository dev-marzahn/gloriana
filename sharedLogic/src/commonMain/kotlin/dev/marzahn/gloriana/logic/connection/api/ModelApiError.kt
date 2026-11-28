package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.keys.KeysBlocked
import io.ktor.http.HttpStatusCode

sealed interface ModelApiError {
  data object Unreachable : ModelApiError

  data object Timeout : ModelApiError

  data class KeyRejected(val status: Int, val body: String) : ModelApiError

  data class RateLimited(val body: String, val retryAfterSeconds: Long?) : ModelApiError

  data class HostError(val status: Int?, val body: String) : ModelApiError

  data object PlainHttpToPublicHost : ModelApiError

  data object BadAddress : ModelApiError

  data object BadHeader : ModelApiError

  data object StreamNotBoolean : ModelApiError

  data object Unexpected : ModelApiError

  data class KeysMissing(val held: HeldSecrets) : ModelApiError

  data class KeysNotOpened(val why: KeysBlocked, val held: HeldSecrets) : ModelApiError
}

internal fun statusError(status: HttpStatusCode, body: String, retryAfter: String?): ModelApiError = when (status) {
  HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> ModelApiError.KeyRejected(status.value, body)
  HttpStatusCode.TooManyRequests -> ModelApiError.RateLimited(body, retryAfterSeconds(retryAfter))
  else -> ModelApiError.HostError(status.value, body)
}

private fun retryAfterSeconds(retryAfter: String?): Long? = retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

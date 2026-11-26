package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers
import kotlinx.serialization.Serializable

private const val BEARER = "Bearer "

@Serializable
internal data class OpenAiError(val error: OpenAiErrorDetail)

@Serializable
internal data class OpenAiErrorDetail(val message: String, val type: String, val param: String?, val code: String?)

internal fun openAiError(failure: Failure): String {
  val (type, code) = when (failure.kind) {
    FailureKind.BAD_REQUEST -> "invalid_request_error" to null
    FailureKind.KEY_REJECTED -> "invalid_request_error" to "invalid_api_key"
    FailureKind.NOT_FOUND -> "invalid_request_error" to "unknown_url"
    FailureKind.RATE_LIMITED -> "requests" to "rate_limit_exceeded"
    FailureKind.HOST_ERROR -> "server_error" to null
  }
  return wireJson.encodeToString(
    OpenAiError.serializer(),
    OpenAiError(OpenAiErrorDetail(failure.message, type, param = null, code = code)),
  )
}

internal fun bearerToken(headers: Headers): String? = headers["Authorization"]
  ?.takeIf { it.startsWith(BEARER, ignoreCase = true) }
  ?.substring(BEARER.length)
  ?.takeIf(String::isNotEmpty)

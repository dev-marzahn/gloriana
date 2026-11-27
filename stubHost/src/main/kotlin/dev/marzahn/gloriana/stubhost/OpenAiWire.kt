package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers
import kotlinx.serialization.Serializable
import java.time.Instant

private const val BEARER = "Bearer "
private const val MODEL_OWNER = "stub-host"
private val modelCreatedSeconds = Instant.parse(CREATED_AT).epochSecond

@Serializable
internal data class OpenAiModelList(val `object`: String = "list", val data: List<OpenAiModel>)

@Serializable
internal data class OpenAiModel(val id: String, val `object`: String = "model", val created: Long, val ownedBy: String)

internal fun openAiModelList(models: List<StubModel>): String = wireJson.encodeToString(
  OpenAiModelList.serializer(),
  OpenAiModelList(data = models.map { OpenAiModel(id = it.id, created = modelCreatedSeconds, ownedBy = MODEL_OWNER) }),
)

@Serializable
internal data class OpenAiError(val error: OpenAiErrorDetail)

@Serializable
internal data class OpenAiErrorDetail(val message: String, val type: String, val param: String?, val code: String?)

internal fun openAiError(failure: Failure): String =
  wireJson.encodeToString(OpenAiError.serializer(), OpenAiError(openAiErrorDetail(failure)))

internal fun openAiErrorDetail(failure: Failure): OpenAiErrorDetail {
  val (type, code) = when (failure.kind) {
    FailureKind.BAD_REQUEST -> "invalid_request_error" to null
    FailureKind.KEY_REJECTED -> "invalid_request_error" to "invalid_api_key"
    FailureKind.FORBIDDEN -> "invalid_request_error" to "unsupported_country_region_territory"
    FailureKind.NOT_FOUND -> "invalid_request_error" to "unknown_url"
    FailureKind.RATE_LIMITED -> "requests" to "rate_limit_exceeded"
    FailureKind.HOST_ERROR -> "server_error" to null
  }
  return OpenAiErrorDetail(failure.message, type, param = null, code = code)
}

internal fun bearerToken(headers: Headers): String? = headers["Authorization"]
  ?.takeIf { it.startsWith(BEARER, ignoreCase = true) }
  ?.substring(BEARER.length)
  ?.takeIf(String::isNotEmpty)

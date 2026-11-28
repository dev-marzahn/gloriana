package dev.marzahn.gloriana.stubhost

import io.ktor.http.HttpStatusCode

internal enum class FailureKind(val status: HttpStatusCode, val retryAfterSeconds: Int? = null) {
  BAD_REQUEST(HttpStatusCode.BadRequest),
  KEY_REJECTED(HttpStatusCode.Unauthorized),
  FORBIDDEN(HttpStatusCode.Forbidden),
  NOT_FOUND(HttpStatusCode.NotFound),
  RATE_LIMITED(HttpStatusCode.TooManyRequests, retryAfterSeconds = 1),
  HOST_ERROR(HttpStatusCode.InternalServerError),
}

internal data class Failure(val kind: FailureKind, val message: String)

package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.delay

internal const val MODELS_PATH = "models"
internal const val CREATED_AT = "2026-01-01T00:00:00Z"
private const val ANTHROPIC_VERSION_HEADER = "anthropic-version"

internal data class StubModel(val id: String, val displayName: String)

internal enum class Listing {
  MODELS,
  EMPTY,
  NONE,
}

internal val stubModels = listOf(
  StubModel("stub-model-large", "Stub Model Large"),
  StubModel("stub-model-small", "Stub Model Small"),
)

internal suspend fun ApplicationCall.listModels(mode: Mode) {
  val format = listingFormat(request.headers)
  delay(mode.startPause)
  when (val outcome = mode.outcome) {
    is Outcome.Replied, is Outcome.CutsOff -> respondListing(format, mode.listing)
    is Outcome.Failed -> respondFailure(format, outcome.failure)
    is Outcome.BreaksOff -> respondFailure(format, outcome.failure)
    is Outcome.EchoesKey -> respondEcho(format, outcome)
    Outcome.Redirects -> respondRedirect()
  }
}

private fun listingFormat(headers: Headers): ApiFormat =
  if (headers.contains(ANTHROPIC_VERSION_HEADER)) AnthropicMessages else ChatCompletions

private suspend fun ApplicationCall.respondListing(format: ApiFormat, listing: Listing) = when (listing) {
  Listing.MODELS -> respondJson(format.modelList(stubModels), HttpStatusCode.OK)
  Listing.EMPTY -> respondJson(format.modelList(emptyList()), HttpStatusCode.OK)
  Listing.NONE -> respondFailure(format, Failure(FailureKind.NOT_FOUND, "The stub host lists no models here."))
}

package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.connection.ContentTypeHeader
import dev.marzahn.gloriana.logic.connection.contentTypeOrNull
import dev.marzahn.gloriana.logic.connection.headerFaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.io.IOException
import kotlin.time.Duration.Companion.seconds

internal class PlainHttpRefused : IOException()

internal fun interface ModelHttpClients {
  fun forUrl(url: Url): HttpClient
}

internal fun modelHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
  expectSuccess = false
  followRedirects = false
  install(HttpTimeout)
}

internal sealed interface HostReply {
  class Body(val text: String) : HostReply

  class Failed(val error: ModelApiError) : HostReply
}

internal sealed interface HostEvent {
  class Data(val text: String) : HostEvent

  class Failed(val error: ModelApiError) : HostEvent
}

internal class HostCalls(private val clients: ModelHttpClients) {
  suspend fun whole(request: HostRequest): HostReply {
    refusal(request)?.let { return HostReply.Failed(it) }
    return failingAs({ HostReply.Failed(failureOf(it)) }) {
      val response = clients.forUrl(request.url).request { applyTo(request) }
      if (response.status.isSuccess()) {
        HostReply.Body(response.bodyAsUtf8())
      } else {
        HostReply.Failed(statusError(response, request.scrubber))
      }
    }
  }

  fun events(request: HostRequest): Flow<HostEvent> = channelFlow {
    val scrubber = request.scrubber
    val failure = refusal(request) ?: failingAs({ failureOf(it) }) {
      clients.forUrl(request.url).prepareRequest {
        applyTo(request)
        accept(ContentType.Text.EventStream)
      }.execute { response ->
        when {
          !response.status.isSuccess() -> statusError(response, scrubber)
          !response.isEventStream() ->
            ModelApiError.HostError(response.status.value, scrubber.scrub(response.bodyAsUtf8()))
          else -> {
            response.bodyAsChannel().readEventData { data -> if (data.isNotBlank()) send(HostEvent.Data(data)) }
            null
          }
        }
      }
    }
    failure?.let { send(HostEvent.Failed(it)) }
  }
}

private fun refusal(request: HostRequest): ModelApiError? = ModelApiError.BadHeader.takeIf {
  request.headers.any { headerFaults(it.name.canonical, it.value).isNotEmpty() }
}

private suspend fun HttpResponse.bodyAsUtf8(): String = readRawBytes().decodeToString()

private fun HttpResponse.isEventStream(): Boolean =
  headers[HttpHeaders.ContentType]?.let(::contentTypeOrNull)?.match(ContentType.Text.EventStream) == true

private suspend fun statusError(response: HttpResponse, scrubber: Scrubber): ModelApiError =
  statusError(response.status, scrubber.scrub(response.bodyAsUtf8()), response.headers[HttpHeaders.RetryAfter])

private fun HttpRequestBuilder.applyTo(request: HostRequest) {
  method = request.method
  url.takeFrom(request.url)
  request.headers.filterNot { it.name == ContentTypeHeader }.forEach { headers.append(it.name.canonical, it.value) }
  val waitMillis = request.timeoutSeconds.seconds.inWholeMilliseconds
  timeout {
    connectTimeoutMillis = waitMillis
    socketTimeoutMillis = waitMillis
    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
  }
  request.body?.let { body ->
    val type = request.headers.lastOrNull { it.name == ContentTypeHeader }?.value?.let(::contentTypeOrNull)
    setBody(TextContent(body.toString(), type ?: ContentType.Application.Json))
  }
}

internal fun failureOf(failure: Throwable): ModelApiError =
  generateSequence(failure) { it.cause }.firstNotNullOfOrNull { cause ->
    when (cause) {
      is PlainHttpRefused -> ModelApiError.PlainHttpToPublicHost
      is ConnectTimeoutException, is SocketTimeoutException -> ModelApiError.Timeout
      else -> null
    }
  } ?: ModelApiError.Unreachable

package dev.marzahn.gloriana.stubhost

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.toMap
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlin.time.Duration

internal const val RECORD_PATH = "/record"
internal val ndJson = ContentType("application", "x-ndjson")

internal fun Application.stubHost(record: RequestRecord, endpoints: Endpoints) {
  routing {
    serveTheRecord(record)
    recordAndAnswerEveryOtherRequest(record, endpoints)
  }
}

private fun Route.serveTheRecord(record: RequestRecord) {
  route(RECORD_PATH) {
    get { call.respondText(record.read(), ndJson) }
    handle { call.respond(HttpStatusCode.MethodNotAllowed) }
  }
}

private fun Route.recordAndAnswerEveryOtherRequest(record: RequestRecord, endpoints: Endpoints) {
  route("{...}") {
    handle {
      val body = call.receiveText()
      record.append(call.request.httpMethod.value, call.request.path(), call.request.headers.toMap(), body)
      when (val target = endpoints.resolve(call.request.httpMethod, call.request.path())) {
        is Target.Model -> call.serve(target, body)
        is Target.NoModel -> call.respondFailure(target.format, Failure(FailureKind.NOT_FOUND, endpoints.howToCall))
        Target.Nowhere -> call.respondText(endpoints.howToCall, ContentType.Text.Plain, HttpStatusCode.NotFound)
      }
    }
  }
}

private suspend fun ApplicationCall.serve(target: Target.Model, body: String) {
  val modelRequest: ModelRequest
  try {
    modelRequest = wireJson.decodeFromString(ModelRequest.serializer(), body)
  } catch (invalid: SerializationException) {
    val reason = "The request body is not valid: ${invalid.message}"
    return respondFailure(target.format, Failure(FailureKind.BAD_REQUEST, reason))
  }
  answer(target, modelRequest)
}

private suspend fun ApplicationCall.answer(target: Target.Model, modelRequest: ModelRequest) {
  val (mode, format) = target
  delay(mode.startPause)
  when (val outcome = mode.outcome) {
    is Outcome.Replied ->
      if (modelRequest.streams) {
        respondStream(format.stream(outcome.reply, modelRequest), mode.eventPause)
      } else {
        respondJson(format.whole(outcome.reply, modelRequest), HttpStatusCode.OK)
      }
    is Outcome.Failed -> respondFailure(format, outcome.failure)
    Outcome.EchoesKey -> respondFailure(format, Outcome.EchoesKey.failure(format.key(request.headers)))
  }
}

private suspend fun ApplicationCall.respondStream(events: List<SseEvent>, eventPause: Duration) {
  respondTextWriter(ContentType.Text.EventStream) {
    events.forEachIndexed { index, event ->
      if (index > 0) delay(eventPause)
      write(event.encoded())
      flush()
    }
  }
}

private suspend fun ApplicationCall.respondFailure(format: ApiFormat, failure: Failure) {
  failure.kind.retryAfterSeconds?.let { response.header(HttpHeaders.RetryAfter, it) }
  respondJson(format.error(failure), failure.kind.status)
}

private suspend fun ApplicationCall.respondJson(json: String, status: HttpStatusCode) =
  respondText(json, ContentType.Application.Json, status)

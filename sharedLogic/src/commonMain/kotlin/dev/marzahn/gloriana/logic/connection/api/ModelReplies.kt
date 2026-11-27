package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.StreamValue
import dev.marzahn.gloriana.logic.connection.streamValue
import dev.marzahn.gloriana.logic.json.parsedObject
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile

internal class ModelReplies(private val host: HostCalls) {
  fun reply(sections: List<PromptSection>, connection: OpenedConnection, global: Samplers): Flow<ModelEvent> {
    val fields = connection.fields
    val adapter = fields.format.adapter
    val body = adapter.body(sections, fields, mergeSamplers(fields.format, global, fields.samplers))
    val request = hostRequest(connection, HttpMethod.Post, adapter.path, body)
      ?: return flowOf(ModelEvent.Failed(ModelApiError.BadAddress))
    return when (fields.streamValue) {
      StreamValue.On -> streamed(request, adapter.streamReader(request.scrubber))
      StreamValue.Off -> whole(request, adapter)
      StreamValue.NotBoolean -> flowOf(ModelEvent.Failed(ModelApiError.StreamNotBoolean))
    }
  }

  private fun streamed(request: HostRequest, reader: StreamReader): Flow<ModelEvent> = flow {
    val read = host.events(request).transformWhile { event ->
      val events = when (event) {
        is HostEvent.Data -> reader.read(event.text)
        is HostEvent.Failed -> reader.failed(event.error)
      }
      events.forEach { emit(it) }
      !reader.over
    }
    emitAll(read)
    reader.ended().forEach { emit(it) }
  }

  private fun whole(request: HostRequest, adapter: FormatAdapter): Flow<ModelEvent> = flow {
    val events = when (val reply = host.whole(request)) {
      is HostReply.Failed -> listOf(ModelEvent.Failed(reply.error))
      is HostReply.Body -> adapter.wholeReply(reply.text, request.scrubber)
    }
    events.forEach { emit(it) }
  }
}

internal fun FormatAdapter.wholeReply(text: String, scrubber: Scrubber): List<ModelEvent> {
  val json = parsedObject(text)
  return if (json == null || isErrorReply(json)) {
    listOf(ModelEvent.Failed(ModelApiError.HostError(status = null, body = scrubber.scrub(text))))
  } else {
    whole(json)
  }
}

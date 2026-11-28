package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.withoutPadding
import dev.marzahn.gloriana.logic.keys.HeaderName
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.parseUrl
import kotlinx.serialization.json.JsonObject

internal class HostHeader(val name: HeaderName, val value: String)

internal class OpenedConnection(val fields: ConnectionFields, val key: String?, val headers: List<HostHeader>)

internal class HostRequest(
  val method: HttpMethod,
  val url: Url,
  val headers: List<HostHeader>,
  val body: JsonObject?,
  val timeoutSeconds: Int,
) {
  val scrubber: Scrubber =
    Scrubber(headers.filter { it.name in credentialHeaders }.flatMap { credentialParts(it.value) }.toSet())
}

private val credentialHeaders: Set<HeaderName> =
  ApiFormat.entries.map { it.adapter.keyHeaderName }.toSet() + listOf("api-key", "x-goog-api-key").map(HeaderName::of)

private fun credentialParts(value: String): List<String> =
  withoutPadding(value).let { sent ->
    listOf(sent, withoutPadding(sent.substringAfter(' ', missingDelimiterValue = ""))).filter(String::isNotEmpty)
  }

internal fun headersWith(own: List<HostHeader>, custom: List<HostHeader>): List<HostHeader> {
  val replaced = custom.mapTo(HashSet(), HostHeader::name)
  return own.filterNot { it.name in replaced } + custom
}

internal fun hostRequest(
  connection: OpenedConnection,
  method: HttpMethod,
  target: String,
  body: JsonObject?,
): HostRequest? {
  val adapter = connection.fields.format.adapter
  val ownHeaders = adapter.fixedHeaders + listOfNotNull(connection.key?.let { adapter.keyHeader(it) })
  val url = parseUrl(addressOf(connection.fields.baseUrl, target)) ?: return null
  return HostRequest(
    method = method,
    url = url,
    headers = headersWith(ownHeaders, connection.headers),
    body = body,
    timeoutSeconds = connection.fields.timeoutSeconds,
  )
}

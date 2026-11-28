package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.withoutTrailingSlash
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url

private const val SCHEME_END = "://"

internal fun addressOf(baseUrl: String, target: String): String = "${withoutTrailingSlash(baseUrl.trim())}/$target"

fun ApiFormat.requestAddress(baseUrl: String): String = addressOf(baseUrl, adapter.path)

fun hostOf(baseUrl: String): String = try {
  val url = Url(baseUrl.trim())
  if (url.port == url.protocol.defaultPort) url.host else "${url.host}:${url.port}"
} catch (_: URLParserException) {
  baseUrl.substringAfter(SCHEME_END).substringBefore('/')
}

fun isPlainHttp(baseUrl: String): Boolean {
  val scheme = baseUrl.trim().substringBefore(SCHEME_END, missingDelimiterValue = "")
  return scheme.equals(URLProtocol.HTTP.name, ignoreCase = true)
}

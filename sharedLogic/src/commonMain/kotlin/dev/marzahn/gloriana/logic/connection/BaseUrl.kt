package dev.marzahn.gloriana.logic.connection

private const val SCHEME_END = "://"
internal const val PLAIN_HTTP = "http"
private val DEFAULT_PORTS = mapOf(PLAIN_HTTP to ":80", "https" to ":443")

class BaseUrl(private val text: String) {
  private val queryStart = text.indexOfFirst { it == '?' || it == '#' }.let { if (it < 0) text.length else it }
  private val schemeEnd: Int? = text.take(queryStart).indexOf(SCHEME_END).takeIf { it >= 0 }
  private val authorityStart: Int = schemeEnd?.plus(SCHEME_END.length) ?: 0

  val scheme: String = schemeEnd?.let { text.take(it).lowercase() }.orEmpty()

  internal val authority: String? = schemeEnd?.let { text.substring(authorityStart, queryStart).substringBefore('/') }

  val isPlainHttp: Boolean get() = scheme == PLAIN_HTTP

  val hostAndPort: String?
    get() = authority?.substringAfterLast('@')?.lowercase()?.removeSuffix(DEFAULT_PORTS[scheme].orEmpty())

  internal fun withoutTrailingSlash(): String = text.take(authorityStart) + text.drop(authorityStart).trimEnd('/')
}

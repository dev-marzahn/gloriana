package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

sealed interface HeaderFault {
  sealed interface OfName : HeaderFault

  data object NameEmpty : OfName

  data class NameNotToken(val character: Char) : OfName

  data object NameReserved : OfName

  data class ValueBadCharacter(val character: Char) : HeaderFault

  data object ContentTypeInvalid : HeaderFault
}

val ContentTypeHeader: HeaderName = HeaderName.of(HttpHeaders.ContentType)

private val reservedHeaders: Set<HeaderName> = listOf(
  HttpHeaders.Host,
  HttpHeaders.ContentLength,
  HttpHeaders.TransferEncoding,
  HttpHeaders.Upgrade,
  HttpHeaders.Connection,
  HttpHeaders.TE,
  HttpHeaders.Expect,
  HttpHeaders.ProxyAuthorization,
  HttpHeaders.AcceptEncoding,
  HttpHeaders.Trailer,
  "Keep-Alive",
).map(HeaderName::of).toSet()

private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"
private const val TAB = '\t'
private val visibleAsciiAndSpace = ' '..'~'

fun headerFaults(name: String, value: String?): Set<HeaderFault> = setOfNotNull(
  nameFault(name),
  value?.let { valueFault(HeaderName.of(name), withoutPadding(it)) },
)

internal fun withoutPadding(value: String): String = value.trim(' ', TAB)

internal fun firstUnsendableCharacter(value: String): Char? =
  value.firstOrNull { it != TAB && it !in visibleAsciiAndSpace }

private fun nameFault(name: String): HeaderFault? {
  val notToken = name.firstOrNull { !isTokenCharacter(it) }
  return when {
    name.isEmpty() -> HeaderFault.NameEmpty
    notToken != null -> HeaderFault.NameNotToken(notToken)
    HeaderName.of(name) in reservedHeaders -> HeaderFault.NameReserved
    else -> null
  }
}

private fun valueFault(name: HeaderName, value: String): HeaderFault? {
  firstUnsendableCharacter(value)?.let { return HeaderFault.ValueBadCharacter(it) }
  return HeaderFault.ContentTypeInvalid.takeIf { name == ContentTypeHeader && contentTypeOrNull(value) == null }
}

private fun isTokenCharacter(character: Char): Boolean =
  character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in TOKEN_SYMBOLS

fun contentTypeOrNull(value: String): ContentType? = try {
  ContentType.parse(value).takeIf { it.contentType.isNotEmpty() && it.contentSubtype.isNotEmpty() }
} catch (_: BadContentTypeFormatException) {
  null
}

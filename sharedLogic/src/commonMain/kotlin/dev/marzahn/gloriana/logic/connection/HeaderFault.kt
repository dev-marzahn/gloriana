package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

enum class HeaderFault {
  NameEmpty,
  NameNotToken,
  NameReserved,
  ValueBadCharacter,
  ContentTypeInvalid,
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
  value?.let { valueFault(HeaderName.of(name), it) },
)

fun isSendableHeaderValue(value: String): Boolean = value.all { it == TAB || it in visibleAsciiAndSpace }

private fun nameFault(name: String): HeaderFault? = when {
  name.isEmpty() -> HeaderFault.NameEmpty
  !name.all(::isTokenCharacter) -> HeaderFault.NameNotToken
  HeaderName.of(name) in reservedHeaders -> HeaderFault.NameReserved
  else -> null
}

private fun valueFault(name: HeaderName, value: String): HeaderFault? = when {
  !isSendableHeaderValue(value) -> HeaderFault.ValueBadCharacter
  name == ContentTypeHeader && contentTypeOrNull(value) == null -> HeaderFault.ContentTypeInvalid
  else -> null
}

private fun isTokenCharacter(character: Char): Boolean =
  character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in TOKEN_SYMBOLS

fun contentTypeOrNull(value: String): ContentType? = try {
  ContentType.parse(value).takeIf { it.contentType.isNotEmpty() && it.contentSubtype.isNotEmpty() }
} catch (_: BadContentTypeFormatException) {
  null
}

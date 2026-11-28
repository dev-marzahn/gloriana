package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlBadCharacter
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlBadPort
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlHasFragment
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlHasQuery
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlHasUserInfo
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlNoHost
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlNotHttp

private const val HIGHEST_PORT = 65_535
private const val IPV6_GROUPS = 8
private const val IPV4_AS_IPV6_GROUPS = 2
private const val IPV4_PARTS = 4
private const val HIGHEST_IPV4_PART = 255
private const val ASCII_END = 0x7F
private val COMBINING_MARKS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK)
private const val NOT_IN_A_URL ="\"<>\\^`{|}"
private val HTTP_SCHEMES = setOf(PLAIN_HTTP, "https")
private val PORT = Regex("[0-9]{1,5}")
private val HEX_GROUP = Regex("[0-9A-Fa-f]{1,4}")
private val IPV4_PART = Regex("[0-9]{1,3}")

internal fun baseUrlProblems(baseUrl: String): Set<ConnectionProblem> = buildSet {
  if (baseUrl.any { it.isWhitespace() || it.isISOControl() || it in NOT_IN_A_URL }) add(BaseUrlBadCharacter)
  if ('#' in baseUrl) add(BaseUrlHasFragment)
  if ('?' in baseUrl) add(BaseUrlHasQuery)
  val parsed = BaseUrl(baseUrl)
  if (parsed.scheme !in HTTP_SCHEMES) add(BaseUrlNotHttp)
  parsed.authority?.let { addAll(authorityProblems(it)) }
}

private fun authorityProblems(authority: String): Set<ConnectionProblem> = buildSet {
  if ('@' in authority) add(BaseUrlHasUserInfo)
  val (host, port) = splitPort(authority.substringAfterLast('@'))
  if (!isHost(host)) add(BaseUrlNoHost)
  if (port != null && !isPort(port)) add(BaseUrlBadPort)
}

private fun splitPort(hostAndPort: String): Pair<String, String?> {
  val hostEnd = if (hostAndPort.startsWith('[')) hostAndPort.indexOf(']') + 1 else hostAndPort.indexOf(':')
  if (hostEnd <= 0 || hostEnd >= hostAndPort.length) return hostAndPort to null
  val rest = hostAndPort.substring(hostEnd)
  return hostAndPort.substring(0, hostEnd) to (if (rest.startsWith(':')) rest.drop(1) else rest)
}

private fun isPort(port: String): Boolean = PORT.matches(port) && port.toInt() in 1..HIGHEST_PORT

private fun isHost(host: String): Boolean =
  if (host.startsWith('[') && host.endsWith(']')) isIpv6(host.drop(1).dropLast(1)) else isRegisteredName(host)

private fun isRegisteredName(host: String): Boolean = host.removeSuffix(".").split('.').all(::isLabel)

private fun isLabel(label: String): Boolean =
  label.isNotEmpty() && !label.startsWith('-') && !label.endsWith('-') && label.all(::isHostCharacter)

private fun isHostCharacter(character: Char): Boolean =
  character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character == '-' || character == '_' ||
    character.code > ASCII_END && (character.isLetter() || character.category in COMBINING_MARKS)

private fun isIpv6(address: String): Boolean {
  val lastGroup = address.substringAfterLast(':')
  val endsInIpv4 = '.' in lastGroup
  if (endsInIpv4 && !isIpv4(lastGroup)) return false
  val head = if (endsInIpv4) address.dropLast(lastGroup.length) else address
  val hexPart = if (!endsInIpv4 || head.endsWith("::")) head else head.removeSuffix(":")
  val groupsNeeded = if (endsInIpv4) IPV6_GROUPS - IPV4_AS_IPV6_GROUPS else IPV6_GROUPS
  val halves = hexPart.split("::")
  val groups = halves.flatMap { half -> if (half.isEmpty()) emptyList() else half.split(':') }
  val shape = halves.size == 1 && groups.size == groupsNeeded || halves.size == 2 && groups.size < groupsNeeded
  return shape && groups.all(HEX_GROUP::matches)
}

private fun isIpv4(address: String): Boolean = address.split('.').let { parts ->
  parts.size == IPV4_PARTS && parts.all { IPV4_PART.matches(it) && it.toInt() <= HIGHEST_IPV4_PART }
}

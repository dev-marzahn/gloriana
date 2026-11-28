package dev.marzahn.gloriana.logic.text

internal inline fun <T> parsedOrNull(parse: () -> T): T? = try {
  parse()
} catch (_: IllegalArgumentException) {
  null
}

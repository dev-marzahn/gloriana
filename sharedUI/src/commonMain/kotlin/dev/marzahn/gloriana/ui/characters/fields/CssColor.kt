package dev.marzahn.gloriana.ui.characters.fields

private val Hex = Regex("""#([0-9a-fA-F]{3,8})""")
private val Rgb = Regex("""rgba?\(\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*(?:[,/]\s*[0-9.%]+\s*)?\)""")
private const val OPAQUE = 0xFF000000
private const val CHANNEL_MAX = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val SHORT_HEX = 3
private const val LONG_HEX = 6
private const val LONG_HEX_ALPHA = 8

private val NamedColors = mapOf(
  "black" to 0x000000L,
  "white" to 0xFFFFFFL,
  "red" to 0xFF0000L,
  "green" to 0x008000L,
  "lime" to 0x00FF00L,
  "blue" to 0x0000FFL,
  "yellow" to 0xFFFF00L,
  "orange" to 0xFFA500L,
  "purple" to 0x800080L,
  "pink" to 0xFFC0CBL,
  "gray" to 0x808080L,
  "grey" to 0x808080L,
  "silver" to 0xC0C0C0L,
  "maroon" to 0x800000L,
  "olive" to 0x808000L,
  "navy" to 0x000080L,
  "teal" to 0x008080L,
  "aqua" to 0x00FFFFL,
  "cyan" to 0x00FFFFL,
  "fuchsia" to 0xFF00FFL,
  "magenta" to 0xFF00FFL,
  "gold" to 0xFFD700L,
  "crimson" to 0xDC143CL,
  "violet" to 0xEE82EEL,
  "indigo" to 0x4B0082L,
  "brown" to 0xA52A2AL,
  "coral" to 0xFF7F50L,
  "salmon" to 0xFA8072L,
  "skyblue" to 0x87CEEBL,
  "hotpink" to 0xFF69B4L,
  "darkred" to 0x8B0000L,
  "darkblue" to 0x00008BL,
  "darkgreen" to 0x006400L,
  "lightblue" to 0xADD8E6L,
  "lightgreen" to 0x90EE90L,
)

fun parseCssColor(value: String): Long? {
  val text = value.trim().lowercase()
  return NamedColors[text]?.let { it or OPAQUE } ?: hex(text) ?: rgb(text)
}

private fun hex(text: String): Long? {
  val digits = Hex.matchEntire(text)?.groupValues?.get(1) ?: return null
  return when (digits.length) {
    SHORT_HEX -> digits.map { "$it$it" }.joinToString("").toLong(radix = 16) or OPAQUE
    LONG_HEX, LONG_HEX_ALPHA -> digits.take(LONG_HEX).toLong(radix = 16) or OPAQUE
    else -> null
  }
}

private fun rgb(text: String): Long? {
  val channels = Rgb.matchEntire(text)?.destructured?.toList()?.map { it.toInt() }
  return channels?.takeIf { all -> all.none { it > CHANNEL_MAX } }?.let { (red, green, blue) ->
    OPAQUE or (red.toLong() shl RED_SHIFT) or (green.toLong() shl GREEN_SHIFT) or blue.toLong()
  }
}

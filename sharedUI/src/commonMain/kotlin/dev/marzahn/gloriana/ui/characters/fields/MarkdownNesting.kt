package dev.marzahn.gloriana.ui.characters.fields

private val Fence = Regex("""^ {0,3}(`{3,}|~{3,})""")
private val QuoteMarker = Regex("""^[ \t]*>[ \t]?""")
private val ListMarker = Regex("""^([ \t]*)(?:[-*+]|\d{1,9}[.)])(?:[ \t]+|$)""")

internal fun markdownNesting(source: String): Int {
  val levels = MarkdownLevels()
  var fence: String? = null
  source.lineSequence().forEach { line ->
    val marker = Fence.find(line)?.groupValues?.get(1)
    val open = fence
    when {
      open != null -> if (marker != null && closes(open, marker, line)) fence = null
      marker != null -> fence = marker
      else -> levels.read(line)
    }
  }
  return levels.deepest
}

private fun closes(opening: String, marker: String, line: String): Boolean =
  marker[0] == opening[0] && marker.length >= opening.length && line.trim().all { it == marker[0] }

private class MarkdownLevels {
  private val openItems = ArrayDeque<Int>()
  var deepest = 0
    private set

  fun read(line: String) {
    var rest = line
    var quotes = 0
    while (true) {
      val quote = QuoteMarker.find(rest) ?: break
      quotes++
      rest = rest.substring(quote.range.last + 1)
    }
    var column = line.length - rest.length
    while (true) {
      val item = ListMarker.find(rest) ?: break
      val indent = column + item.groupValues[1].length
      while (openItems.isNotEmpty() && openItems.last() >= indent) openItems.removeLast()
      openItems.addLast(indent)
      column += item.value.length
      rest = rest.substring(item.value.length)
    }
    deepest = maxOf(deepest, quotes + openItems.size)
  }
}

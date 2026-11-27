package dev.marzahn.gloriana.ui.characters.fields

private val StyleTags = mapOf(
  "b" to InlineStyle.Bold,
  "strong" to InlineStyle.Bold,
  "i" to InlineStyle.Italic,
  "em" to InlineStyle.Italic,
  "cite" to InlineStyle.Italic,
  "u" to InlineStyle.Underline,
  "ins" to InlineStyle.Underline,
  "s" to InlineStyle.Strike,
  "del" to InlineStyle.Strike,
  "strike" to InlineStyle.Strike,
  "mark" to InlineStyle.Mark,
  "small" to InlineStyle.Small,
  "big" to InlineStyle.Big,
  "sub" to InlineStyle.Subscript,
  "sup" to InlineStyle.Superscript,
)

private val TaggedInlines: Map<String, FieldInlines.(Part.Tag) -> List<Inline>> = mapOf(
  "code" to { listOf(Inline.Code(plainText(it.children))) },
  "kbd" to { listOf(Inline.Keyboard(plainText(it.children))) },
  "q" to { listOf(Inline.Quoted(of(it.children))) },
  "font" to { colored(it.attribute("color"), of(it.children)) },
  "span" to { span(it) },
  "a" to { linked(it.attribute("href"), of(it.children)) },
  "ruby" to { listOf(ruby(it)) },
  "rb" to { of(it.children) },
  "rt" to { of(it.children) },
  "img" to { listOf(Inline.Image(it.attribute("alt"))) },
  "br" to { listOf(Inline.LineBreak) },
)

internal val ListedTags = StyleTags.keys + TaggedInlines.keys + BlockTags

private val StyleColor = Regex("""(?:^|;)\s*color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
private val LinkSchemes = setOf("http", "https", "mailto")
private val RubyAnnotations = setOf("rt", "rp")

internal class FieldInlines(private val writtenMarker: String) {
  fun of(parts: List<Part>): List<Inline> = parts.flatMap(::inline).merged()

  private fun inline(part: Part): List<Inline> = when (part) {
    is Part.Text -> text(part.text)
    is Part.Written -> listOf(Inline.AsWritten(part.text))
    is Part.Tag -> tag(part)
  }

  private fun tag(tag: Part.Tag): List<Inline> {
    val style = StyleTags[tag.name]
    val tagged = TaggedInlines[tag.name]
    return when {
      style != null -> listOf(Inline.Styled(style, of(tag.children)))
      tagged != null -> tagged(tag)
      tag.name in BlockTags -> of(tag.children) + Inline.LineBreak
      else -> asWritten(tag)
    }
  }

  fun span(tag: Part.Tag): List<Inline> =
    if (writtenMarker in tag.attributes) {
      listOf(Inline.AsWritten(plainText(tag.children)))
    } else {
      colored(StyleColor.find(tag.attribute("style"))?.groupValues?.get(1), of(tag.children))
    }

  private fun asWritten(tag: Part.Tag): List<Inline> =
    listOf(Inline.AsWritten(tag.start)) + of(tag.children) + listOfNotNull(tag.end?.let(Inline::AsWritten))
}

internal fun plainText(parts: List<Part>, skipping: Set<String> = emptySet()): String =
  parts.joinToString("") { part ->
    when (part) {
      is Part.Text -> part.text
      is Part.Written -> part.text
      is Part.Tag -> when {
        part.name in skipping -> ""
        part.name == "br" -> "\n"
        part.name in ListedTags -> plainText(part.children, skipping)
        else -> part.start + plainText(part.children, skipping) + part.end.orEmpty()
      }
    }
  }

private fun text(text: String): List<Inline> = text.split('\n').flatMapIndexed { index, line ->
  listOfNotNull(Inline.LineBreak.takeIf { index > 0 }, Inline.Text(line))
}

private fun ruby(tag: Part.Tag): Inline {
  val annotations = tag.children.filterIsInstance<Part.Tag>().filter { it.name == "rt" }
  val base = plainText(tag.children, skipping = RubyAnnotations)
  return Inline.Ruby(base, plainText(annotations.flatMap { it.children }))
}

private fun linked(href: String, children: List<Inline>): List<Inline> {
  val address = href.trim()
  val scheme = address.substringBefore(':', missingDelimiterValue = "").lowercase()
  return if (scheme in LinkSchemes) listOf(Inline.Link(address, children)) else children
}

private fun colored(value: String?, children: List<Inline>): List<Inline> =
  value?.takeIf { it.isNotBlank() }?.let(::parseCssColor)?.let { listOf(Inline.Colored(it, children)) } ?: children

internal fun List<Inline>.trimmedText(): List<Inline> {
  val edges = dropWhile { it == Inline.LineBreak }.dropLastWhile { it == Inline.LineBreak }.toMutableList()
  edges.firstOrNull()?.let { edges[0] = it.trimmed(String::trimStart) }
  edges.lastOrNull()?.let { edges[edges.lastIndex] = it.trimmed(String::trimEnd) }
  return edges.merged()
}

private fun Inline.trimmed(trim: (String) -> String): Inline = when (this) {
  is Inline.Text -> Inline.Text(trim(text))
  is Inline.AsWritten -> Inline.AsWritten(trim(text))
  else -> this
}

internal fun List<Inline>.merged(): List<Inline> = fold(mutableListOf()) { merged, inline ->
  val last = merged.lastOrNull()
  if (inline is Inline.Text && last is Inline.Text) {
    merged[merged.lastIndex] = Inline.Text(last.text + inline.text)
  } else if (inline !is Inline.Text || inline.text.isNotEmpty()) {
    merged += inline
  }
  merged
}

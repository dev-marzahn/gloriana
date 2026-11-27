package dev.marzahn.gloriana.ui.characters.fields

import kotlin.random.Random

internal const val MAX_NESTING = 64

fun parseFieldMarkup(source: String): List<FieldBlock> {
  if (markdownNesting(source) > MAX_NESTING) return listOf(FieldBlock.AsWritten(source))
  val writtenMarker = "data-written-${Random.nextLong().toULong().toString(radix = 16)}"
  val rawText = liftRawTextElements(source, writtenMarker)
  val html = markdownToHtml(rawText.source, writtenMarker)
  return FieldBlocks(writtenMarker).blocks(writtenTree(html, rawText.elements))
}


private val HeadingLevels = (1..6).associateBy { "h$it" }
private val PassThroughBlocks = setOf("p", "div", "center", "li", "summary")

private val StructuralBlocks: Map<String, FieldBlocks.(Part.Tag) -> FieldBlock> = mapOf(
  "hr" to { FieldBlock.Rule },
  "ul" to { itemList(it, ordered = false) },
  "ol" to { itemList(it, ordered = true) },
  "blockquote" to { FieldBlock.Quote(blocks(it.children)) },
  "pre" to { preformatted(it) },
  "details" to { details(it) },
)

internal val BlockTags = HeadingLevels.keys + PassThroughBlocks + StructuralBlocks.keys

internal class FieldBlocks(private val writtenMarker: String) {
  private val inlines = FieldInlines(writtenMarker)

  fun blocks(parts: List<Part>): List<FieldBlock> {
    val result = mutableListOf<FieldBlock>()
    val pending = mutableListOf<Part>()
    fun flush() {
      result += paragraphs(pending)
      pending.clear()
    }
    parts.forEach { part ->
      when {
        part is Part.Tag && part.name in BlockTags -> {
          flush()
          result += block(part)
        }
        part is Part.Tag && part.name !in ListedTags && part.holdsBlocks() -> {
          flush()
          result += unlistedWrapper(part)
        }
        else -> pending += part
      }
    }
    flush()
    return result
  }

  private fun unlistedWrapper(tag: Part.Tag): List<FieldBlock> =
    listOf(FieldBlock.AsWritten(tag.start)) + blocks(tag.children) + listOfNotNull(tag.end?.let(FieldBlock::AsWritten))

  private fun paragraphs(parts: List<Part>): List<FieldBlock> =
    if (parts.all { it is Part.Text && it.text.isBlank() }) emptyList() else splitImages(inlines.of(parts))

  private fun block(tag: Part.Tag): List<FieldBlock> {
    val level = HeadingLevels[tag.name]
    val structural = StructuralBlocks[tag.name]
    return when {
      level != null -> listOf(FieldBlock.Heading(level, inlines.of(tag.children).trimmedText()))
      structural != null -> listOf(structural(tag))
      else -> blocks(tag.children)
    }
  }

  fun preformatted(tag: Part.Tag): FieldBlock {
    val text = plainText(tag.children).removeSuffix("\n")
    return if (writtenMarker in tag.attributes) FieldBlock.AsWritten(text) else FieldBlock.Preformatted(text)
  }

  fun itemList(tag: Part.Tag, ordered: Boolean): FieldBlock.ItemList = FieldBlock.ItemList(
    ordered = ordered,
    start = tag.attribute("start").toIntOrNull() ?: 1,
    items = tag.children.filterIsInstance<Part.Tag>().filter { it.name == "li" }.map(::listItem),
  )

  private fun listItem(item: Part.Tag): ListItem {
    val (boxes, content) = item.children.partition { it is Part.Tag && it.isCheckbox() }
    val box = boxes.filterIsInstance<Part.Tag>().firstOrNull()
    val mark = box?.let { if ("checked" in it.attributes) TaskMark.Done else TaskMark.Open }
    return ListItem(mark, blocks(content))
  }

  fun details(tag: Part.Tag): FieldBlock.Details {
    val (summaries, body) = tag.children.partition { it is Part.Tag && it.name == "summary" }
    val summary = summaries.filterIsInstance<Part.Tag>().firstOrNull()?.let { inlines.of(it.children).trimmedText() }
    return FieldBlock.Details(summary.orEmpty(), blocks(body))
  }
}

private fun Part.Tag.isCheckbox(): Boolean = name == "input" && attribute("type").equals("checkbox", ignoreCase = true)

private fun Part.Tag.holdsBlocks(): Boolean =
  children.any { it is Part.Tag && (it.name in BlockTags || it.holdsBlocks()) }

private fun splitImages(inlines: List<Inline>): List<FieldBlock> {
  val blocks = mutableListOf<FieldBlock>()
  var run = mutableListOf<Inline>()
  fun flush() {
    val trimmed = run.trimmedText()
    if (trimmed.any { it !is Inline.Text || it.text.isNotBlank() }) blocks += FieldBlock.Paragraph(trimmed)
    run = mutableListOf()
  }
  inlines.forEach { inline ->
    if (inline is Inline.Image) {
      flush()
      blocks += FieldBlock.ImageNotLoaded(inline.alt)
    } else {
      run += inline
    }
  }
  flush()
  return blocks
}

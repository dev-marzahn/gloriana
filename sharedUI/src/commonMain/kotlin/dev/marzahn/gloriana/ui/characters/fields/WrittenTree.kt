package dev.marzahn.gloriana.ui.characters.fields

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.CDataNode
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.parser.ParseSettings
import com.fleeksoft.ksoup.parser.Parser

internal val VoidTags = setOf("br", "hr", "img", "input", "wbr", "meta", "link", "source", "area", "col", "embed")

private val UncountedTags = setOf("li", "p")
private const val ELEMENTS_PER_LEVEL = 4

private data class Depth(val levels: Int, val elements: Int) {
  fun into(name: String, nests: Boolean) =
    Depth(if (!nests || name in UncountedTags) levels else levels + 1, elements + 1)

  val tooDeep: Boolean get() = levels > MAX_NESTING || elements > MAX_NESTING * ELEMENTS_PER_LEVEL
}

internal fun writtenTree(html: String, rawText: Map<String, RawTextElement>): List<Part> {
  val parser = Parser.xmlParser().settings(ParseSettings.htmlDefault).setTrackPosition(true)
  val document = Ksoup.parse(html, parser)
  return WrittenTreeBuilder(html, rawText).children(document, 0, html.length, Depth(0, 0))
}

private class WrittenTreeBuilder(private val html: String, private val rawText: Map<String, RawTextElement>) {
  fun children(parent: Node, from: Int, to: Int, depth: Depth): List<Part> {
    val parts = mutableListOf<Part>()
    var cursor = from
    parent.childNodes().forEach { node ->
      val start = node.sourceRange().startPos()
      if (start > cursor) parts += Part.Written(source(cursor, start))
      parts += parts(node, depth)
      cursor = maxOf(cursor, end(node))
    }
    if (to > cursor) parts += Part.Written(source(cursor, to))
    return parts
  }

  private fun parts(node: Node, depth: Depth): List<Part> = when (node) {
    is CDataNode -> listOf(Part.Written(slice(node)))
    is TextNode -> listOf(Part.Text(restored(node.getWholeText())))
    is Element -> element(node, depth)
    else -> lifted(node) ?: listOf(Part.Written(slice(node)))
  }

  private fun lifted(node: Node): List<Part>? {
    val range = node.sourceRange()
    return rawText[html.substring(range.startPos(), range.endPos())]?.let {
      listOf(Part.Written(it.open), Part.Text(it.content), Part.Written(it.close))
    }
  }

  private fun restored(text: String): String =
    rawText.entries.fold(text) { result, (placeholder, element) -> result.replace(placeholder, element.original) }

  private fun source(from: Int, to: Int): String = restored(html.substring(from, to))

  private fun element(element: Element, parentDepth: Depth): List<Part> {
    val name = element.normalName()
    val endRange = element.endSourceRange()
    val writtenEnd = endRange.isTracked() && !endRange.isImplicit()
    val markerOnly = !writtenEnd && name !in ListedTags && name !in VoidTags
    val depth = parentDepth.into(name, nests = !markerOnly)
    return if (depth.tooDeep) {
      listOf(Part.Written(source(element.sourceRange().startPos(), end(element))))
    } else {
      shaped(element, writtenEnd, markerOnly, depth)
    }
  }

  private fun shaped(element: Element, writtenEnd: Boolean, markerOnly: Boolean, depth: Depth): List<Part> {
    val name = element.normalName()
    val startRange = element.sourceRange()
    val endRange = element.endSourceRange()
    val contentEnd = if (writtenEnd) endRange.startPos() else end(element)
    val inner = children(element, startRange.endPos(), contentEnd, depth)
    val start = source(startRange.startPos(), startRange.endPos())
    if (markerOnly) return listOf(Part.Written(start)) + inner
    val tag = Part.Tag(
      name = name,
      attributes = element.attributes().associate { it.key to restored(it.value) },
      start = start,
      end = if (writtenEnd) source(endRange.startPos(), endRange.endPos()) else null,
      children = inner,
    )
    return when (name) {
      in VoidTags -> listOf(tag.copy(children = emptyList())) + inner
      "li" -> splitAtNestedItems(tag)
      else -> listOf(tag)
    }
  }

  private fun slice(node: Node): String = source(node.sourceRange().startPos(), node.sourceRange().endPos())

  private fun end(node: Node): Int {
    var furthest = node.sourceRange().endPos()
    val pending = ArrayDeque(listOf(node))
    while (pending.isNotEmpty()) {
      val next = pending.removeLast()
      val ownEnd = (next as? Element)?.endSourceRange()?.takeIf { it.isTracked() }?.endPos() ?: 0
      furthest = maxOf(furthest, next.sourceRange().endPos(), ownEnd)
      pending.addAll(next.childNodes())
    }
    return furthest
  }
}

private fun splitAtNestedItems(item: Part.Tag): List<Part> {
  val result = mutableListOf<Part>()
  var segment = mutableListOf<Part>()
  var current = item
  fun flush() {
    val written = current === item || segment.any { it !is Part.Text || it.text.isNotBlank() }
    if (written) result += current.copy(children = segment)
    current = Part.Tag("li", emptyMap(), start = "", end = null, children = emptyList())
    segment = mutableListOf()
  }
  item.children.forEach { child ->
    if (child is Part.Tag && child.name == "li") {
      flush()
      result += child
    } else {
      segment += child
    }
  }
  flush()
  return result
}

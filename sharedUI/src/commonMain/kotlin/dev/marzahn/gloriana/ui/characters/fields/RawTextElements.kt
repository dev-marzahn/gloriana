package dev.marzahn.gloriana.ui.characters.fields

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

internal data class RawTextElement(val open: String, val content: String, val close: String) {
  val original: String get() = open + content + close
}

internal class LiftedRawText(val source: String, val elements: Map<String, RawTextElement>)

private const val RAW_TEXT_TAGS = "script|style|textarea|title|xmp|iframe|noembed|noframes|noscript"
private val RawTextPattern = Regex("""(<($RAW_TEXT_TAGS)\b[^>]*>)([\s\S]*?)(</\2\s*>)""", RegexOption.IGNORE_CASE)
private const val OPEN_TAG = 1
private const val CONTENT = 3
private const val CLOSE_TAG = 4
private val CodeTypes =
  setOf(MarkdownElementTypes.CODE_SPAN, MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK)

internal fun liftRawTextElements(source: String, marker: String): LiftedRawText {
  val code = codeRanges(source)
  val elements = mutableMapOf<String, RawTextElement>()
  val lifted = RawTextPattern.replace(source) { match ->
    if (code.any { match.range.first in it }) {
      match.value
    } else {
      val placeholder = "<!--$marker-${elements.size}-->"
      val groups = match.groupValues
      elements[placeholder] = RawTextElement(groups[OPEN_TAG], groups[CONTENT], groups[CLOSE_TAG])
      placeholder
    }
  }
  return LiftedRawText(lifted, elements)
}

private fun codeRanges(source: String): List<IntRange> {
  val parser = MarkdownParser(GFMFlavourDescriptor(), assertionsEnabled = false, CancellationToken.NonCancellable)
  val text: CharSequence = source
  val pending = ArrayDeque<ASTNode>(listOf(parser.buildMarkdownTreeFromString(text)))
  val ranges = mutableListOf<IntRange>()
  while (pending.isNotEmpty()) {
    val node = pending.removeLast()
    if (node.type in CodeTypes) ranges += node.startOffset until node.endOffset else pending.addAll(node.children)
  }
  return ranges
}

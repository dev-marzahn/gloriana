package dev.marzahn.gloriana.ui.characters.fields

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser
import org.intellij.markdown.html.EqualDelimiterTrimmingInlineTagProvider
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.URI
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl.AutolinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser
import org.intellij.markdown.parser.sequentialparsers.impl.ImageParser
import org.intellij.markdown.parser.sequentialparsers.impl.InlineLinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.ReferenceLinkParser

private val TableLines = setOf(GFMElementTypes.HEADER, GFMElementTypes.ROW, GFMTokenTypes.TABLE_SEPARATOR)

private fun escapedHtml(text: CharSequence): String =
  EntityConverter.replaceEntities(text, processEntities = false, processEscapes = false)

private object GfmInlinesWithoutMathOrHighlight : SequentialParserManager() {
  override fun getParserSequence(): List<SequentialParser> = listOf(
    AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
    BacktickParser(),
    ImageParser(),
    InlineLinkParser(),
    ReferenceLinkParser(),
    EmphasisLikeParser(EmphStrongDelimiterParser(), StrikeThroughDelimiterParser()),
  )
}

private object ChildrenWithoutWrapper : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    node.acceptChildren(visitor)
  }
}

private object AlertAsQuoteWithItsMarker : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeTagOpen(node, "blockquote")
    node.children.forEach { child ->
      if (child.type == GFMTokenTypes.ALERT_TITLE) {
        visitor.consumeHtml("<p>${escapedHtml(child.getTextInNode(text))}</p>")
      } else {
        child.accept(visitor)
      }
    }
    visitor.consumeTagClose("blockquote")
  }
}

private class TableAsWritten(private val marker: String) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val lines = node.children.filter { it.type in TableLines }.joinToString("\n") { it.getTextInNode(text).trim() }
    visitor.consumeTagOpen(node, "pre", marker)
    visitor.consumeHtml(escapedHtml(lines))
    visitor.consumeTagClose("pre")
  }
}

private val InlineTag = Regex("""<(/?)\s*([a-zA-Z][a-zA-Z0-9-]*)[^>]*?(/?)>""")
private const val END_SLASH = 1
private const val TAG_NAME = 2
private const val SELF_CLOSING = 3

private fun MatchResult.isEndTag(): Boolean = groupValues[END_SLASH].isNotEmpty()

private class StrayInlineEndTagAsWritten(private val marker: String) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val tag = node.getTextInNode(text).toString()
    val stray = node.parent?.type != MarkdownElementTypes.HTML_BLOCK && closesNothingOpenBefore(node, text, tag)
    visitor.consumeHtml(if (stray) "<span $marker>${escapedHtml(tag)}</span>" else tag)
  }

  private fun closesNothingOpenBefore(node: ASTNode, text: String, tag: String): Boolean {
    val endTagName = InlineTag.matchEntire(tag)?.takeIf { it.isEndTag() }?.groupValues?.get(TAG_NAME)
    return endTagName != null && endTagName.lowercase() in BlockTags && openCount(node, text, endTagName) <= 0
  }

  private fun openCount(node: ASTNode, text: String, name: String): Int =
    node.parent?.children.orEmpty().takeWhile { it !== node }
      .filter { it.type == MarkdownTokenTypes.HTML_TAG }
      .mapNotNull { InlineTag.matchEntire(it.getTextInNode(text)) }
      .filter { it.groupValues[TAG_NAME].equals(name, ignoreCase = true) && it.groupValues[SELF_CLOSING].isEmpty() }
      .sumOf { if (it.isEndTag()) -1 else 1 }
}

private class FieldFlavour(private val marker: String) : GFMFlavourDescriptor(useSafeLinks = false) {
  override val sequentialParserManager: SequentialParserManager = GfmInlinesWithoutMathOrHighlight

  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> =
    super.createHtmlGeneratingProviders(linkMap, baseURI) + mapOf(
      MarkdownElementTypes.MARKDOWN_FILE to ChildrenWithoutWrapper,
      MarkdownTokenTypes.HTML_TAG to StrayInlineEndTagAsWritten(marker),
      GFMElementTypes.TABLE to TableAsWritten(marker),
      GFMElementTypes.ALERT to AlertAsQuoteWithItsMarker,
      GFMElementTypes.STRIKETHROUGH to EqualDelimiterTrimmingInlineTagProvider("del", GFMTokenTypes.TILDE),
    )
}

internal fun markdownToHtml(source: String, writtenMarker: String): String {
  val flavour = FieldFlavour(writtenMarker)
  val parser = MarkdownParser(flavour, assertionsEnabled = false, CancellationToken.NonCancellable)
  val text: CharSequence = source
  return HtmlGenerator(source, parser.buildMarkdownTreeFromString(text), flavour).generateHtml()
}

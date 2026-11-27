package dev.marzahn.gloriana.ui.characters.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_open_in_new_20
import dev.marzahn.gloriana.ui.resources.image_not_loaded
import dev.marzahn.gloriana.ui.resources.image_not_loaded_alt
import dev.marzahn.gloriana.ui.resources.opens_in_browser
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.LookColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SMALL = 0.85f
private const val BIG = 1.15f
private const val SCRIPT = 0.75f
private const val SCRIPT_SHIFT = 0.33f
private const val CODE = 0.9f
private const val RUBY_ANNOTATION = 0.5f
private const val MIN_CONTRAST = 3f
private const val CONTRAST_OFFSET = 0.05f
private const val LINK_ICON = "link-icon"
private const val RUBY_PREFIX = "ruby-"
private const val LINK_ICON_EM = 0.875f
private const val EM_SAMPLE = "M"
private val CodeRadius = 4.dp
private val KeyboardEdge = 2.dp
private val Underline = 1.dp
private val CodePadding = 2.dp

private enum class DecorationKind { Code, Keyboard, Link }

private class Decoration(val kind: DecorationKind, val start: Int, val end: Int)

private class FieldTextBuilder(
  private val colors: LookColors,
  private val panel: Color,
  private val measure: (AnnotatedString, TextStyle) -> Size,
  private val base: TextStyle,
  private val words: ImageWords,
) {
  val decorations = mutableListOf<Decoration>()
  val rubies = mutableListOf<Inline.Ruby>()
  val builder = AnnotatedString.Builder()

  fun build(inlines: List<Inline>): AnnotatedString {
    inlines.forEach(::append)
    return builder.toAnnotatedString()
  }

  private fun append(inline: Inline) {
    when (inline) {
      is Inline.Text -> builder.append(inline.text)
      is Inline.Styled -> builder.withStyle(styleFor(inline.style)) { inline.children.forEach(::append) }
      is Inline.Link -> link(inline)
      is Inline.Colored -> colored(inline)
      is Inline.Quoted -> quoted(inline)
      is Inline.Code -> decorated(DecorationKind.Code, inline.text)
      is Inline.Keyboard -> decorated(DecorationKind.Keyboard, inline.text)
      is Inline.Ruby -> ruby(inline)
      is Inline.AsWritten -> builder.withStyle(SpanStyle(color = colors.textMuted)) { append(inline.text) }
      is Inline.Image -> builder.withStyle(SpanStyle(color = colors.textSubtle)) { append(words.text(inline.alt)) }
      Inline.LineBreak -> builder.append("\n")
    }
  }

  private fun styleFor(style: InlineStyle): SpanStyle = when (style) {
    InlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    InlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    InlineStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    InlineStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    InlineStyle.Mark -> SpanStyle(background = colors.surface3)
    InlineStyle.Small -> SpanStyle(fontSize = SMALL.em)
    InlineStyle.Big -> SpanStyle(fontSize = BIG.em)
    InlineStyle.Subscript -> SpanStyle(fontSize = SCRIPT.em, baselineShift = BaselineShift(-SCRIPT_SHIFT))
    InlineStyle.Superscript -> SpanStyle(fontSize = SCRIPT.em, baselineShift = BaselineShift(SCRIPT_SHIFT))
  }

  private fun link(link: Inline.Link) {
    val start = builder.length
    val plain = TextLinkStyles(SpanStyle(color = colors.text))
    builder.withLink(LinkAnnotation.Url(link.url, plain)) { link.children.forEach(::append) }
    decorations += Decoration(DecorationKind.Link, start, builder.length)
    builder.append(" ")
    builder.appendInlineContent(LINK_ICON, words.opensInBrowser)
  }

  private fun colored(colored: Inline.Colored) {
    val color = Color(colored.argb.toInt())
    if (contrast(color, panel) >= MIN_CONTRAST) {
      builder.withStyle(SpanStyle(color = color)) { colored.children.forEach(::append) }
    } else {
      colored.children.forEach(::append)
    }
  }

  private fun quoted(quoted: Inline.Quoted) {
    builder.append('“')
    quoted.children.forEach(::append)
    builder.append('”')
  }

  private fun decorated(kind: DecorationKind, text: String) {
    val start = builder.length
    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = CODE.em)) {
      append(" $text ")
    }
    decorations += Decoration(kind, start, builder.length)
  }

  private fun ruby(ruby: Inline.Ruby) {
    rubies += ruby
    builder.appendInlineContent(RUBY_PREFIX + (rubies.size - 1), ruby.base)
  }

  fun rubyContent(): Map<String, InlineTextContent> = rubies.mapIndexed { index, ruby ->
    val annotationStyle = base.copy(fontSize = base.fontSize * RUBY_ANNOTATION, color = colors.textMuted)
    val baseSize = measure(AnnotatedString(ruby.base), base)
    val annotationSize = measure(AnnotatedString(ruby.annotation), annotationStyle)
    val emPx = measure(AnnotatedString(EM_SAMPLE), base).width
    val placeholder = Placeholder(
      width = (maxOf(baseSize.width, annotationSize.width) / emPx).em,
      height = ((baseSize.height + annotationSize.height) / emPx).em,
      placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
    )
    (RUBY_PREFIX + index) to InlineTextContent(placeholder) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ruby.annotation, style = annotationStyle)
        Text(ruby.base, style = base)
      }
    }
  }.toMap()
}

private class ImageWords(val loaded: String, val withAltSlot: String, val opensInBrowser: String) {
  fun text(alt: String): String = if (alt.isBlank()) loaded else withAltSlot.replace(ALT_SLOT, alt)
}

private fun contrast(foreground: Color, background: Color): Float {
  val lighter = maxOf(foreground.luminance(), background.luminance())
  val darker = minOf(foreground.luminance(), background.luminance())
  return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

@Composable
fun FieldText(inlines: List<Inline>, style: TextStyle, panel: Color, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val loaded = stringResource(Res.string.image_not_loaded)
  val withAltSlot = stringResource(Res.string.image_not_loaded_alt, ALT_SLOT)
  val opensInBrowser = stringResource(Res.string.opens_in_browser)
  val built = remember(inlines, style, colors, panel, density, loaded, withAltSlot, opensInBrowser) {
    val words = ImageWords(loaded, withAltSlot, opensInBrowser)
    val builder = FieldTextBuilder(colors, panel, measurer.sizer(density), style, words)
    Triple(builder.build(inlines), builder.decorations.toList(), builder.rubyContent())
  }
  val (text, decorations, rubies) = built
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val linkIcon = InlineTextContent(Placeholder(LINK_ICON_EM.em, LINK_ICON_EM.em, PlaceholderVerticalAlign.Center)) {
    Icon(painterResource(Res.drawable.ic_open_in_new_20), null, Modifier.fillMaxSize(), tint = colors.textSubtle)
  }
  Text(
    text,
    modifier.drawBehind { layout?.let { drawDecorations(it, decorations, colors) } },
    style = style.copy(color = colors.text),
    inlineContent = rubies + (LINK_ICON to linkIcon),
    onTextLayout = { layout = it },
  )
}

private const val ALT_SLOT = ""

private class LineBox(val left: Float, val right: Float, val top: Float, val bottom: Float, val baseline: Float)

private fun TextMeasurer.sizer(density: Density): (AnnotatedString, TextStyle) -> Size = { text, style ->
  val result = measure(text, style, density = density)
  Size(result.size.width.toFloat(), result.size.height.toFloat())
}

private fun DrawScope.drawDecorations(layout: TextLayoutResult, decorations: List<Decoration>, colors: LookColors) {
  decorations.forEach { decoration ->
    val end = decoration.end.coerceAtMost(layout.layoutInput.text.length)
    if (decoration.start >= end) return@forEach
    val firstLine = layout.getLineForOffset(decoration.start)
    val lastLine = layout.getLineForOffset(end - 1)
    for (line in firstLine..lastLine) {
      val starts = line == firstLine
      val ends = line == lastLine
      val box = LineBox(
        left = if (starts) layout.getHorizontalPosition(decoration.start, true) else layout.getLineLeft(line),
        right = if (ends) layout.getHorizontalPosition(end, true) else layout.getLineRight(line),
        top = layout.getLineTop(line),
        bottom = layout.getLineBottom(line),
        baseline = layout.getLineBaseline(line),
      )
      drawDecoration(decoration.kind, box, colors)
    }
  }
}

private fun DrawScope.drawDecoration(kind: DecorationKind, box: LineBox, colors: LookColors) {
  if (kind == DecorationKind.Link) {
    val y = box.baseline + Underline.toPx() * 2
    drawLine(colors.textSubtle, Offset(box.left, y), Offset(box.right, y), strokeWidth = Underline.toPx())
    return
  }
  val inset = CodePadding.toPx()
  val topLeft = Offset(box.left, box.top + inset)
  val size = Size(box.right - box.left, box.bottom - box.top - inset * 2)
  val radius = CornerRadius(CodeRadius.toPx())
  drawRoundRect(colors.surface2, topLeft, size, radius)
  drawRoundRect(colors.borderSubtle, topLeft, size, radius, style = Stroke(Underline.toPx()))
  if (kind == DecorationKind.Keyboard) {
    val y = topLeft.y + size.height
    val edge = KeyboardEdge.toPx()
    drawLine(colors.borderStrong, Offset(box.left + radius.x, y), Offset(box.right - radius.x, y), strokeWidth = edge)
  }
}

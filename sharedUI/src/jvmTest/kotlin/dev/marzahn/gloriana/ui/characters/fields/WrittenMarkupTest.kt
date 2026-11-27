package dev.marzahn.gloriana.ui.characters.fields

import dev.marzahn.gloriana.ui.characters.fields.FieldBlock.Paragraph
import dev.marzahn.gloriana.ui.characters.fields.Inline.AsWritten
import dev.marzahn.gloriana.ui.characters.fields.Inline.Styled
import dev.marzahn.gloriana.ui.characters.fields.Inline.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DEEP = 5_000
private const val DEEP_INDENT = 1_500
private const val ONE_MEGABYTE = 1L shl 20

class WrittenMarkupTest {
  private fun paragraph(vararg inlines: Inline) = listOf(Paragraph(inlines.toList()))

  private fun bold(vararg inlines: Inline) = Styled(InlineStyle.Bold, inlines.toList())

  private fun table(vararg lines: String) = FieldBlock.AsWritten(lines.joinToString("\n"))

  @Test
  fun `a stray closing tag shows as written`() {
    assertEquals(paragraph(Text("a"), AsWritten("</custom>"), Text("b")), parseFieldMarkup("a</custom>b"))
    assertEquals(paragraph(Text("text"), AsWritten("</aside>")), parseFieldMarkup("text</aside>"))
    assertEquals(
      paragraph(Text("Hello "), AsWritten("</aside>"), Text(" world")),
      parseFieldMarkup("Hello </aside> world"),
    )
    assertEquals(paragraph(bold(Text("x"), AsWritten("</i>"), Text("y"))), parseFieldMarkup("<b>x</i>y</b>"))
    assertEquals(paragraph(Inline.Code("</aside>")), parseFieldMarkup("<code></aside></code>"))
    assertEquals(listOf(FieldBlock.Preformatted("</aside>")), parseFieldMarkup("<pre></aside></pre>"))
  }

  @Test
  fun `a stray list end tag keeps the list together`() {
    val expected = listOf(
      FieldBlock.ItemList(
        ordered = false,
        start = 1,
        items = listOf(
          ListItem(null, paragraph(Text("a"), AsWritten("</ul>"), Text("b"))),
          ListItem(null, paragraph(Text("c"))),
        ),
      ),
    )
    assertEquals(expected, parseFieldMarkup("- a</ul>b\n- c"))
  }

  @Test
  fun `a bare less-than inside a script keeps the script's text`() {
    val script = paragraph(AsWritten("<script>"), Text("if (a<b && c) x()"), AsWritten("</script>"))
    assertEquals(script, parseFieldMarkup("<script>if (a<b && c) x()</script>"))
    assertEquals(
      paragraph(Text("run "), AsWritten("<script>"), Text("if (a<b && c) x()"), AsWritten("</script>")),
      parseFieldMarkup("run <script>if (a<b && c) x()</script>"),
    )
  }

  @Test
  fun `tag and attribute names are read in any case`() {
    assertEquals(paragraph(bold(Text("x")), Text(" y")), parseFieldMarkup("<B>x</b> y"))
    assertEquals(
      paragraph(Inline.Colored(0xFFFF0000, listOf(Text("red")))),
      parseFieldMarkup("<Font Color=\"red\">red</Font>"),
    )
  }

  @Test
  fun `a GFM alert keeps its marker as text`() {
    listOf("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION").forEach { kind ->
      assertEquals(
        listOf(FieldBlock.Quote(listOf(Paragraph(listOf(Text("[!$kind]"))), Paragraph(listOf(Text("body")))))),
        parseFieldMarkup("> [!$kind]\n> body"),
        kind,
      )
    }
  }

  @Test
  fun `raw checkboxes mark their list items and keep their text`() {
    val expected = listOf(
      FieldBlock.ItemList(
        ordered = false,
        start = 1,
        items = listOf(
          ListItem(TaskMark.Done, paragraph(Text("done"))),
          ListItem(TaskMark.Open, paragraph(Text("todo"))),
        ),
      ),
    )
    val html = "<ul><li><input type=\"checkbox\" checked>done</li><li><input type=\"checkbox\">todo</li></ul>"
    assertEquals(expected, parseFieldMarkup(html))
  }

  @Test
  fun `an unlisted wrapper keeps the blocks inside it`() {
    val expected = listOf(
      FieldBlock.AsWritten("<section>"),
      FieldBlock.Heading(1, listOf(Text("Title"))),
      FieldBlock.ItemList(false, 1, listOf(ListItem(null, paragraph(Text("a"))), ListItem(null, paragraph(Text("b"))))),
      FieldBlock.AsWritten("</section>"),
    )
    assertEquals(expected, parseFieldMarkup("<section>\n\n# Title\n\n- a\n- b\n\n</section>"))
  }

  @Test
  fun `ruby base text keeps its written order`() {
    assertEquals(paragraph(Inline.Ruby("漢字", "k")), parseFieldMarkup("<ruby><rb>漢</rb>字<rt>k</rt></ruby>"))
  }

  @Test
  fun `only the markdown strikethrough strikes, not a look-alike span`() {
    assertEquals(paragraph(Styled(InlineStyle.Strike, listOf(Text("gone")))), parseFieldMarkup("~~gone~~"))
    assertEquals(paragraph(Text("kept")), parseFieldMarkup("<span class=\"user-del\">kept</span>"))
  }

  @Test
  fun `a user's pre can't pass for a table`() {
    assertEquals(listOf(FieldBlock.Preformatted("hi")), parseFieldMarkup("<pre data-as-written>hi</pre>"))
    assertEquals(listOf(FieldBlock.Preformatted("hi")), parseFieldMarkup("<pre data-written-0>hi</pre>"))
  }

  @Test
  fun `a table inside a quote or a list shows without its container markers`() {
    val written = table("| a | b |", "|---|---|", "| 1 | 2 |")
    assertEquals(listOf(FieldBlock.Quote(listOf(written))), parseFieldMarkup("> | a | b |\n> |---|---|\n> | 1 | 2 |"))
    assertEquals(
      listOf(FieldBlock.ItemList(false, 1, listOf(ListItem(null, listOf(written))))),
      parseFieldMarkup("- | a | b |\n  |---|---|\n  | 1 | 2 |"),
    )
  }

  @Test
  fun `cdata, comments and processing instructions show exactly as written`() {
    val cdata = paragraph(Text("a "), AsWritten("<![CDATA[raw]]>"), Text(" b"))
    assertEquals(cdata, parseFieldMarkup("a <![CDATA[raw]]> b"))
    assertEquals(paragraph(AsWritten("<!-- unclosed")), parseFieldMarkup("<!-- unclosed"))
    assertEquals(paragraph(AsWritten("<?php echo 1; ?>")), parseFieldMarkup("<?php echo 1; ?>"))
  }

  @Test
  fun `links open only web and mail addresses`() {
    assertEquals(paragraph(Text("x")), parseFieldMarkup("[x](javascript:alert(1))"))
    assertEquals(paragraph(Text("x")), parseFieldMarkup("<a href=\"javascript:alert(1)\">x</a>"))
    assertEquals(paragraph(Inline.Link("mailto:a@b.c", listOf(Text("m")))), parseFieldMarkup("[m](mailto:a@b.c)"))
  }

  private fun divs(levels: Int) = "<div>".repeat(levels) + "x" + "</div>".repeat(levels)

  private fun quotes(levels: Int) = ">".repeat(levels) + " x"

  private fun nestedQuotes(levels: Int, innermost: List<FieldBlock>): List<FieldBlock> =
    (1..levels).fold(innermost) { inner, _ -> listOf(FieldBlock.Quote(inner)) }

  @Test
  fun `sixty-four levels render and the sixty-fifth shows as written`() {
    assertEquals(paragraph(Text("x")), parseFieldMarkup(divs(MAX_NESTING)))
    assertEquals(paragraph(AsWritten("<div>x</div>")), parseFieldMarkup(divs(MAX_NESTING + 1)))
    assertEquals(nestedQuotes(MAX_NESTING, paragraph(Text("x"))), parseFieldMarkup(quotes(MAX_NESTING)))
    val tooDeep = quotes(MAX_NESTING + 1)
    assertEquals(listOf(FieldBlock.AsWritten(tooDeep)), parseFieldMarkup(tooDeep))
  }

  @Test
  fun `ordinary cards don't trip the nesting limit`() {
    val starts = parseFieldMarkup("<START>\nhi\n\n".repeat(70)).toString()
    assertEquals(70, Regex.escape("AsWritten(text=<START>)").toRegex().findAll(starts).count())
    assertEquals(70, Regex.escape("Text(text=hi)").toRegex().findAll(starts).count())
    val items = "<ul>" + "<li>item".repeat(70) + "</ul>"
    assertEquals(70, (parseFieldMarkup(items).single() as FieldBlock.ItemList).items.size)
    val indented = "```\n" + " ".repeat(140) + "x\n```"
    assertEquals(listOf(FieldBlock.Preformatted(" ".repeat(140) + "x")), parseFieldMarkup(indented))
  }

  @Test
  fun `thousands of levels render as written without crashing`() {
    listOf("- ".repeat(DEEP) + "x", ">".repeat(DEEP) + "x").forEach { deep ->
      assertEquals(listOf(FieldBlock.AsWritten(deep)), parseFieldMarkup(deep))
    }
    listOf("<b>".repeat(DEEP) + "x", divs(DEEP)).forEach { deep ->
      val shown = parseFieldMarkup(deep).toString()
      assertTrue("AsWritten" in shown && "x" in shown, deep.take(10))
    }
  }

  private fun onSmallStack(block: () -> Unit) {
    var failure: Throwable? = null
    val thread = Thread(null, { runCatching(block).onFailure { failure = it } }, "small-stack", ONE_MEGABYTE)
    thread.start()
    thread.join()
    failure?.let { throw it }
  }

  @Test
  fun `deeply indented lists and quotes show as written on a one-megabyte stack`() {
    val indentedList = (0 until DEEP_INDENT).joinToString("\n") { "  ".repeat(it) + "- x" }
    val listAndQuotes = (0 until DEEP_INDENT).joinToString("\n") { "  ".repeat(it) + if (it % 2 == 0) "- x" else "> x" }
    onSmallStack {
      assertEquals(listOf(FieldBlock.AsWritten(indentedList)), parseFieldMarkup(indentedList))
      assertEquals(listOf(FieldBlock.AsWritten(listAndQuotes)), parseFieldMarkup(listAndQuotes))
    }
  }

  @Test
  fun `markers inside a fence are code, not nesting`() {
    listOf(">".repeat(70), "- ".repeat(70) + "x").forEach { line ->
      assertEquals(listOf(FieldBlock.Preformatted(line)), parseFieldMarkup("```\n$line\n```"))
    }
  }

  @Test
  fun `a lifted raw-text element never leaks its placeholder`() {
    val script = "<script>x</script>"
    val shownInText = listOf("\\$script", "| a |\n|---|\n| $script |", "[l](http://a.b/$script)", "<http://a.b/$script>")
    val inAttributes = listOf("<a title=\"$script\">t</a>", "<div title=\"$script\">t</div>")
    (shownInText + inAttributes).forEach { source ->
      val shown = parseFieldMarkup(source).toString()
      assertTrue("data-written" !in shown, "$source -> $shown")
      if (source in shownInText) assertTrue(script in shown, "$source -> $shown")
    }
  }

  @Test
  fun `raw-text elements show their content as written in the source`() {
    listOf("iframe", "noscript", "title", "script").forEach { tag ->
      assertEquals(
        paragraph(Text("a "), AsWritten("<$tag>"), Text("**b** <i>c</i>"), AsWritten("</$tag>"), Text(" d")),
        parseFieldMarkup("a <$tag>**b** <i>c</i></$tag> d"),
        tag,
      )
    }
    assertEquals(paragraph(Inline.Code("<script>x</script>")), parseFieldMarkup("`<script>x</script>`"))
  }

  @Test
  fun `a nested list item keeps the text after it in written order`() {
    val items = (parseFieldMarkup("<ul><li>a<li>b</li>c</li></ul>").single() as FieldBlock.ItemList).items
    assertEquals(listOf(paragraph(Text("a")), paragraph(Text("b")), paragraph(Text("c"))), items.map { it.blocks })
  }

  @Test
  fun `math and highlight markers stay literal`() {
    assertEquals(paragraph(Text("costs \$x\$ or ==hi==")), parseFieldMarkup("costs \$x\$ or ==hi=="))
  }

  @Test
  fun `bare less-than, ampersands and odd brackets stay in the prose`() {
    assertEquals(paragraph(Text("1 < 2 & 3")), parseFieldMarkup("1 < 2 & 3"))
    assertEquals(paragraph(Text("ok "), AsWritten("<sigh>"), Text(" fine")), parseFieldMarkup("ok <sigh> fine"))
    assertEquals(paragraph(Text("<"), AsWritten("<name>"), Text(">")), parseFieldMarkup("<<name>>"))
  }
}

package dev.marzahn.gloriana.ui.characters.fields

import dev.marzahn.gloriana.ui.characters.fields.FieldBlock.Paragraph
import dev.marzahn.gloriana.ui.characters.fields.Inline.AsWritten
import dev.marzahn.gloriana.ui.characters.fields.Inline.Styled
import dev.marzahn.gloriana.ui.characters.fields.Inline.Text
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldMarkupTest {
  private fun paragraph(vararg inlines: Inline) = listOf(Paragraph(inlines.toList()))

  private fun bold(vararg inlines: Inline) = Styled(InlineStyle.Bold, inlines.toList())

  private fun italic(vararg inlines: Inline) = Styled(InlineStyle.Italic, inlines.toList())

  @Test
  fun `plain text is one paragraph`() {
    assertEquals(paragraph(Text("Hello there")), parseFieldMarkup("Hello there"))
  }

  @Test
  fun `each listed style tag draws its style`() {
    val tags = mapOf(
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
    tags.forEach { (tag, style) ->
      assertEquals(paragraph(Styled(style, listOf(Text("x")))), parseFieldMarkup("<$tag>x</$tag>"), tag)
    }
  }

  @Test
  fun `code, kbd, q and ruby draw as their own forms`() {
    assertEquals(paragraph(Inline.Code("a<b")), parseFieldMarkup("<code>a&lt;b</code>"))
    assertEquals(paragraph(Inline.Keyboard("Ctrl")), parseFieldMarkup("<kbd>Ctrl</kbd>"))
    assertEquals(paragraph(Inline.Quoted(listOf(Text("hi")))), parseFieldMarkup("<q>hi</q>"))
    assertEquals(paragraph(Inline.Ruby("漢", "kan")), parseFieldMarkup("<ruby>漢<rt>kan</rt></ruby>"))
  }

  @Test
  fun `font and span colours are read, other styles are not`() {
    assertEquals(
      paragraph(Inline.Colored(0xFFFF0000, listOf(Text("red")))),
      parseFieldMarkup("<font color=\"red\">red</font>"),
    )
    assertEquals(
      paragraph(Inline.Colored(0xFF336699, listOf(Text("blue")))),
      parseFieldMarkup("<span style=\"font-size: 40px; color: #369\">blue</span>"),
    )
    assertEquals(paragraph(Text("big")), parseFieldMarkup("<span style=\"font-size: 40px\">big</span>"))
  }

  @Test
  fun `a link keeps its address`() {
    assertEquals(
      paragraph(Inline.Link("https://example.com", listOf(Text("site")))),
      parseFieldMarkup("<a href=\"https://example.com\">site</a>"),
    )
  }

  @Test
  fun `tags not on the list show as written, and what is inside still draws`() {
    assertEquals(
      paragraph(AsWritten("<custom>"), bold(Text("a")), Text(" "), bold(Text("b")), AsWritten("</custom>")),
      parseFieldMarkup("<custom><b>a</b> **b**</custom>"),
    )
    assertEquals(
      paragraph(AsWritten("<script>"), Text("alert(1)"), AsWritten("</script>")),
      parseFieldMarkup("<script>alert(1)</script>"),
    )
    assertEquals(paragraph(AsWritten("<!-- note -->"), Text("x")), parseFieldMarkup("<!-- note -->x"))
  }

  @Test
  fun `an unclosed tag not on the list shows its opening as written and keeps its text`() {
    assertEquals(paragraph(AsWritten("<custom>"), Text("prose")), parseFieldMarkup("<custom>prose"))
  }

  @Test
  fun `markdown emphasis, strikethrough and code`() {
    assertEquals(
      paragraph(bold(Text("b")), Text(" "), italic(Text("i")), Text(" "), italic(Text("u"))),
      parseFieldMarkup("**b** *i* _u_"),
    )
    assertEquals(paragraph(Styled(InlineStyle.Strike, listOf(Text("gone")))), parseFieldMarkup("~~gone~~"))
    assertEquals(paragraph(Inline.Code("<b>not bold</b>")), parseFieldMarkup("`<b>not bold</b>`"))
  }

  @Test
  fun `emphasis does not open inside words with underscores or around spaces`() {
    assertEquals(paragraph(Text("snake_case_name")), parseFieldMarkup("snake_case_name"))
    assertEquals(paragraph(Text("2 * 3 * 4")), parseFieldMarkup("2 * 3 * 4"))
  }

  @Test
  fun `bold around italic nests`() {
    assertEquals(paragraph(bold(Text("a "), italic(Text("b")), Text(" c"))), parseFieldMarkup("**a *b* c**"))
  }

  @Test
  fun `markdown links and images`() {
    assertEquals(
      paragraph(Inline.Link("https://x.org", listOf(Text("go")))),
      parseFieldMarkup("[go](https://x.org)"),
    )
    assertEquals(listOf(FieldBlock.ImageNotLoaded("cat")), parseFieldMarkup("![cat](https://x.org/cat.png)"))
    assertEquals(
      listOf(Paragraph(listOf(Text("before"))), FieldBlock.ImageNotLoaded(""), Paragraph(listOf(Text("after")))),
      parseFieldMarkup("before <img src=\"data:image/png;base64,AA\"> after"),
    )
  }

  @Test
  fun `bare and angle-bracket addresses become links`() {
    assertEquals(
      paragraph(Text("see "), Inline.Link("https://a.b/c", listOf(Text("https://a.b/c"))), Text(".")),
      parseFieldMarkup("see https://a.b/c."),
    )
    assertEquals(
      paragraph(Inline.Link("https://a.b", listOf(Text("https://a.b")))),
      parseFieldMarkup("<https://a.b>"),
    )
  }

  @Test
  fun `headings from markdown and html`() {
    assertEquals(listOf(FieldBlock.Heading(2, listOf(Text("Title")))), parseFieldMarkup("## Title"))
    assertEquals(listOf(FieldBlock.Heading(5, listOf(Text("Small")))), parseFieldMarkup("<h5>Small</h5>"))
  }

  @Test
  fun `lists, task lists and nesting`() {
    val expected = listOf(
      FieldBlock.ItemList(
        ordered = false,
        start = 1,
        items = listOf(
          ListItem(null, listOf(Paragraph(listOf(Text("one"))))),
          ListItem(
            null,
            listOf(
              Paragraph(listOf(Text("two"))),
              FieldBlock.ItemList(true, 1, listOf(ListItem(null, listOf(Paragraph(listOf(Text("inner"))))))),
            ),
          ),
        ),
      ),
    )
    assertEquals(expected, parseFieldMarkup("- one\n- two\n  1. inner"))
    assertEquals(
      listOf(FieldBlock.ItemList(true, 3, listOf(ListItem(null, paragraph(Text("third")))))),
      parseFieldMarkup("3. third"),
    )
    assertEquals(
      listOf(
        FieldBlock.ItemList(
          false,
          1,
          listOf(
            ListItem(TaskMark.Done, listOf(Paragraph(listOf(Text("done"))))),
            ListItem(TaskMark.Open, listOf(Paragraph(listOf(Text("todo"))))),
          ),
        ),
      ),
      parseFieldMarkup("- [x] done\n- [ ] todo"),
    )
  }

  @Test
  fun `html lists`() {
    assertEquals(
      listOf(
        FieldBlock.ItemList(
          true,
          1,
          listOf(ListItem(null, paragraph(Text("a"))), ListItem(null, paragraph(Text("b")))),
        ),
      ),
      parseFieldMarkup("<ol><li>a<li>b</ol>"),
    )
  }

  @Test
  fun `quotes, rules and code blocks`() {
    assertEquals(listOf(FieldBlock.Quote(paragraph(Text("said")))), parseFieldMarkup("> said"))
    assertEquals(listOf(FieldBlock.Rule), parseFieldMarkup("---"))
    assertEquals(listOf(FieldBlock.Preformatted("<b>x</b>\n  y")), parseFieldMarkup("```\n<b>x</b>\n  y\n```"))
    assertEquals(listOf(FieldBlock.Preformatted("keep  space")), parseFieldMarkup("<pre>keep  <b>space</b></pre>"))
  }

  @Test
  fun `tables and footnotes show as written`() {
    val table = "| a | b |\n|---|---|\n| 1 | 2 |"
    assertEquals(listOf(FieldBlock.AsWritten(table)), parseFieldMarkup(table))
    assertEquals(paragraph(Text("note[^1]")), parseFieldMarkup("note[^1]"))
  }

  @Test
  fun `an html table shows as written in its written order`() {
    assertEquals(
      paragraph(
        AsWritten("<table>"), AsWritten("<tr>"), AsWritten("<td>"), Text("cell"),
        AsWritten("</td>"), AsWritten("</tr>"), AsWritten("</table>"),
      ),
      parseFieldMarkup("<table><tr><td>cell</td></tr></table>"),
    )
  }

  @Test
  fun `a style block shows as written with its rules`() {
    assertEquals(
      paragraph(AsWritten("<style>"), Text("p { color: red }"), AsWritten("</style>")),
      parseFieldMarkup("<style>p { color: red }</style>"),
    )
  }

  @Test
  fun `details fold with their summary`() {
    assertEquals(
      listOf(FieldBlock.Details(listOf(Text("More")), paragraph(Text("hidden")))),
      parseFieldMarkup("<details><summary>More</summary>hidden</details>"),
    )
  }

  @Test
  fun `line breaks and paragraphs`() {
    assertEquals(
      listOf(Paragraph(listOf(Text("a"), Inline.LineBreak, Text("b"))), Paragraph(listOf(Text("c")))),
      parseFieldMarkup("a\nb\n\nc"),
    )
    assertEquals(paragraph(Text("a"), Inline.LineBreak, Text("b")), parseFieldMarkup("a<br>b"))
  }

  @Test
  fun `macros and entities`() {
    assertEquals(paragraph(Text("{{char}} & {{user}} ©")), parseFieldMarkup("{{char}} &amp; {{user}} &copy;"))
  }

  @Test
  fun `escaped markdown stays literal`() {
    assertEquals(paragraph(Text("*not italic*")), parseFieldMarkup("\\*not italic\\*"))
  }
}

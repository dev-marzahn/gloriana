package dev.marzahn.gloriana.ui.characters.fields

sealed interface FieldBlock {
  data class Paragraph(val inlines: List<Inline>) : FieldBlock

  data class Heading(val level: Int, val inlines: List<Inline>) : FieldBlock

  data class ItemList(val ordered: Boolean, val start: Int, val items: List<ListItem>) : FieldBlock

  data class Quote(val blocks: List<FieldBlock>) : FieldBlock

  data class Preformatted(val text: String) : FieldBlock

  data object Rule : FieldBlock

  data class Details(val summary: List<Inline>, val blocks: List<FieldBlock>) : FieldBlock

  data class ImageNotLoaded(val alt: String) : FieldBlock

  data class AsWritten(val text: String) : FieldBlock
}

enum class TaskMark { Open, Done }

data class ListItem(val task: TaskMark?, val blocks: List<FieldBlock>)

enum class InlineStyle { Bold, Italic, Underline, Strike, Mark, Small, Big, Subscript, Superscript }

sealed interface Inline {
  data class Text(val text: String) : Inline

  data class Styled(val style: InlineStyle, val children: List<Inline>) : Inline

  data class Link(val url: String, val children: List<Inline>) : Inline

  data class Colored(val argb: Long, val children: List<Inline>) : Inline

  data class Quoted(val children: List<Inline>) : Inline

  data class Code(val text: String) : Inline

  data class Keyboard(val text: String) : Inline

  data class Ruby(val base: String, val annotation: String) : Inline

  data class AsWritten(val text: String) : Inline

  data class Image(val alt: String) : Inline

  data object LineBreak : Inline
}

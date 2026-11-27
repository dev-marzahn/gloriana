package dev.marzahn.gloriana.ui.characters.fields

internal sealed interface Part {
  data class Text(val text: String) : Part

  data class Written(val text: String) : Part

  data class Tag(
    val name: String,
    val attributes: Map<String, String>,
    val start: String,
    val end: String?,
    val children: List<Part>,
  ) : Part {
    fun attribute(key: String): String = attributes[key].orEmpty()
  }
}

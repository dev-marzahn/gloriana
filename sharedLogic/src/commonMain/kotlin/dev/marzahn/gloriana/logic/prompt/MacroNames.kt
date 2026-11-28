package dev.marzahn.gloriana.logic.prompt

data class MacroNames(val character: String, val user: String)

private val nameMacro = Regex("""\{\{(char|user)}}""", RegexOption.IGNORE_CASE)

fun String.withNamesFilled(names: MacroNames): String = nameMacro.replace(this) { match ->
  if (match.groupValues[1].equals("char", ignoreCase = true)) names.character else names.user
}

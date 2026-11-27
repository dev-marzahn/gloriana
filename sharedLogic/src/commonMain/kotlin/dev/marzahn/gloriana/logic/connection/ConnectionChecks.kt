package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName

private const val HEADER_TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"

internal fun ConnectionFields.normalised(): ConnectionFields =
  copy(name = name.trim(), model = model.trim(), baseUrl = withoutTrailingSlash(baseUrl.trim()))

internal fun ConnectionFields.problems(): Set<ConnectionProblem> = buildSet {
  if (name.isEmpty()) add(ConnectionProblem.NameEmpty)
  if (model.isEmpty()) add(ConnectionProblem.ModelEmpty)
  addAll(baseUrlProblems(baseUrl))
  if (contextSize <= 0) add(ConnectionProblem.ContextSizeNotPositive)
  if (maxReplyTokens <= 0) add(ConnectionProblem.MaxReplyTokensNotPositive)
  if (contextSize > 0 && maxReplyTokens > 0 && maxReplyTokens >= contextSize) {
    add(ConnectionProblem.MaxReplyTokensNotBelowContextSize)
  }
  if (timeoutSeconds <= 0) add(ConnectionProblem.TimeoutNotPositive)
  samplers.notFinite().forEach { add(ConnectionProblem.SamplerNotFinite(it)) }
}

internal fun List<HeaderDraft>.problems(): Set<ConnectionProblem> = buildSet {
  val seen = mutableSetOf<HeaderName>()
  this@problems.forEachIndexed { index, header ->
    when {
      header.name.isEmpty() -> add(ConnectionProblem.HeaderNameEmpty(index))
      !header.name.all(::isHeaderTokenCharacter) -> add(ConnectionProblem.HeaderNameNotToken(index))
      !seen.add(HeaderName.of(header.name)) -> add(ConnectionProblem.HeaderNameRepeated(index))
    }
    val value = header.value
    if (value is HeaderValueInput.Entered && value.value.any { it == '\r' || it == '\n' }) {
      add(ConnectionProblem.HeaderValueLineBreak(index))
    }
  }
}

private fun isHeaderTokenCharacter(character: Char): Boolean =
  character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in HEADER_TOKEN_SYMBOLS

package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName

internal fun ConnectionFields.normalised(): ConnectionFields =
  copy(name = name.trim(), model = model.trim(), baseUrl = withoutTrailingSlash(baseUrl.trim()))

internal fun ConnectionFields.problems(): Set<ConnectionProblem> =
  textProblems(name, model, baseUrl) + numberProblems(contextSize, maxReplyTokens, timeoutSeconds) +
    samplerProblems(samplers, format.samplerRanges)

fun textProblems(name: String, model: String, baseUrl: String): Set<ConnectionProblem> = buildSet {
  if (name.trim().isEmpty()) add(ConnectionProblem.NameEmpty)
  if (model.trim().isEmpty()) add(ConnectionProblem.ModelEmpty)
  addAll(baseUrlProblems(withoutTrailingSlash(baseUrl.trim())))
}

fun numberProblems(contextSize: Int?, maxReplyTokens: Int?, timeoutSeconds: Int?): Set<ConnectionProblem> = buildSet {
  if (contextSize != null && contextSize <= 0) add(ConnectionProblem.ContextSizeNotPositive)
  if (maxReplyTokens != null && maxReplyTokens <= 0) add(ConnectionProblem.MaxReplyTokensNotPositive)
  if (contextSize != null && maxReplyTokens != null && !replyFitsIn(contextSize, maxReplyTokens)) {
    add(ConnectionProblem.MaxReplyTokensNotBelowContextSize)
  }
  if (timeoutSeconds != null && timeoutSeconds <= 0) add(ConnectionProblem.TimeoutNotPositive)
}

private fun replyFitsIn(contextSize: Int, maxReplyTokens: Int): Boolean =
  contextSize <= 0 || maxReplyTokens < contextSize

fun samplerProblems(samplers: Samplers, ranges: Map<Sampler, SamplerRange>): List<ConnectionProblem> =
  Sampler.entries.mapNotNull { sampler ->
    val value = sampler.valueIn(samplers) ?: return@mapNotNull null
    val range = ranges[sampler]
    if (range != null) {
      ConnectionProblem.SamplerOutOfRange(sampler, range).takeUnless { range.holds(value) }
    } else {
      ConnectionProblem.SamplerNotFinite(sampler).takeIf { sampler.kind.isNonFinite(value) }
    }
  }

internal sealed interface DraftCheck {
  class Ready(val fields: ConnectionFields, val sources: SecretSources) : DraftCheck

  class Refused(val problems: Set<ConnectionProblem>) : DraftCheck
}

internal fun ConnectionDraft.check(
  stored: Connection?,
  alsoRefused: Set<ConnectionProblem> = emptySet(),
  waivedFieldProblems: Set<ConnectionProblem> = emptySet(),
): DraftCheck {
  val normalised = fields.normalised()
  val lookup = lookUpSources(stored, this)
  val problems =
    (normalised.problems() - waivedFieldProblems) + key.problems() + headers.problems() + lookup.gone + alsoRefused
  return if (problems.isEmpty()) DraftCheck.Ready(normalised, lookup.sources) else DraftCheck.Refused(problems)
}

fun KeyInput.problems(): Set<ConnectionProblem> = setOfNotNull(
  (this as? KeyInput.Entered)?.value?.let(::firstUnsendableCharacter)?.let(ConnectionProblem::KeyBadCharacter),
)

fun List<HeaderDraft>.problems(): Set<ConnectionProblem> = buildSet {
  val seen = mutableSetOf<HeaderName>()
  this@problems.forEachIndexed { index, header ->
    val entered = (header.value as? HeaderValueInput.Entered)?.value
    val faults = headerFaults(header.name, entered)
    faults.forEach { add(it.problem(index)) }
    val nameIsUsable = faults.none { it is HeaderFault.OfName }
    if (nameIsUsable && !seen.add(HeaderName.of(header.name))) add(ConnectionProblem.HeaderNameRepeated(index))
  }
}

private fun HeaderFault.problem(index: Int): ConnectionProblem = when (this) {
  HeaderFault.NameEmpty -> ConnectionProblem.HeaderNameEmpty(index)
  is HeaderFault.NameNotToken -> ConnectionProblem.HeaderNameNotToken(index, character)
  HeaderFault.NameReserved -> ConnectionProblem.HeaderNameReserved(index)
  is HeaderFault.ValueBadCharacter -> ConnectionProblem.HeaderValueBadCharacter(index, character)
  HeaderFault.ContentTypeInvalid -> ConnectionProblem.HeaderContentTypeInvalid(index)
}

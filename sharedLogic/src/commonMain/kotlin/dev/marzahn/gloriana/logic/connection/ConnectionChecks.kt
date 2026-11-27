package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.HeaderName

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
  addAll(samplerProblems(samplers, format.samplerRanges))
}

internal fun samplerProblems(samplers: Samplers, ranges: Map<Sampler, SamplerRange>): List<ConnectionProblem> =
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

internal fun KeyInput.problems(): Set<ConnectionProblem> =
  setOfNotNull(ConnectionProblem.KeyBadCharacter.takeIf { this is KeyInput.Entered && !isSendableHeaderValue(value) })

internal fun List<HeaderDraft>.problems(): Set<ConnectionProblem> = buildSet {
  val seen = mutableSetOf<HeaderName>()
  this@problems.forEachIndexed { index, header ->
    val entered = (header.value as? HeaderValueInput.Entered)?.value
    val faults = headerFaults(header.name, entered)
    faults.forEach { add(it.problem(index)) }
    val nameIsUsable = faults.none { it in nameFaults }
    if (nameIsUsable && !seen.add(HeaderName.of(header.name))) add(ConnectionProblem.HeaderNameRepeated(index))
  }
}

private val nameFaults = setOf(HeaderFault.NameEmpty, HeaderFault.NameNotToken, HeaderFault.NameReserved)

private fun HeaderFault.problem(index: Int): ConnectionProblem = when (this) {
  HeaderFault.NameEmpty -> ConnectionProblem.HeaderNameEmpty(index)
  HeaderFault.NameNotToken -> ConnectionProblem.HeaderNameNotToken(index)
  HeaderFault.NameReserved -> ConnectionProblem.HeaderNameReserved(index)
  HeaderFault.ValueBadCharacter -> ConnectionProblem.HeaderValueBadCharacter(index)
  HeaderFault.ContentTypeInvalid -> ConnectionProblem.HeaderContentTypeInvalid(index)
}

package dev.marzahn.gloriana.logic.connection

sealed interface ConnectionProblem {
  data object NameEmpty : ConnectionProblem

  data object NameTaken : ConnectionProblem

  data object ModelEmpty : ConnectionProblem

  data object BaseUrlNotHttp : ConnectionProblem

  data object BaseUrlNoHost : ConnectionProblem

  data object BaseUrlBadPort : ConnectionProblem

  data object BaseUrlHasUserInfo : ConnectionProblem

  data object BaseUrlHasQuery : ConnectionProblem

  data object BaseUrlHasFragment : ConnectionProblem

  data object BaseUrlBadCharacter : ConnectionProblem

  data object ContextSizeNotPositive : ConnectionProblem

  data object MaxReplyTokensNotPositive : ConnectionProblem

  data object MaxReplyTokensNotBelowContextSize : ConnectionProblem

  data object TimeoutNotPositive : ConnectionProblem

  data class SamplerOutOfRange(val sampler: Sampler, val range: SamplerRange) : ConnectionProblem

  data class SamplerNotFinite(val sampler: Sampler) : ConnectionProblem

  data object KeyBadCharacter : ConnectionProblem

  data class HeaderNameEmpty(val index: Int) : ConnectionProblem

  data class HeaderNameNotToken(val index: Int) : ConnectionProblem

  data class HeaderNameRepeated(val index: Int) : ConnectionProblem

  data class HeaderNameReserved(val index: Int) : ConnectionProblem

  data class HeaderValueBadCharacter(val index: Int) : ConnectionProblem

  data class HeaderContentTypeInvalid(val index: Int) : ConnectionProblem

  data class HeaderValueGone(val index: Int) : ConnectionProblem

  data class HeaderValueMissing(val index: Int) : ConnectionProblem
}

package dev.marzahn.gloriana.detekt

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class GlorianaRuleSetProvider : RuleSetProvider {
  override val ruleSetId = RuleSetId("gloriana")

  override fun instance() = RuleSet(ruleSetId, mapOf(RuleName(HardcodedUiText.NAME) to ::HardcodedUiText))
}

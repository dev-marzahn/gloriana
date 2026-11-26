package dev.marzahn.gloriana.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlorianaRuleSetProviderTest {
  private val ruleSet = GlorianaRuleSetProvider().instance()

  @Test
  fun providesHardcodedUiTextUnderItsClassName() {
    val rule = assertNotNull(ruleSet.rules[RuleName(HardcodedUiText.NAME)]).invoke(Config.empty)
    assertEquals(RuleName(HardcodedUiText.NAME), rule.ruleName)
  }

  @Test
  fun bundledConfigOpensWithTheRuleSetIdThenHardcodedUiTextActive() {
    val bundled = assertNotNull(javaClass.getResource("/config/config.yml")).readText()
    val switchedOn = "${ruleSet.id.value}:\n  ${HardcodedUiText.NAME}:\n    active: true\n"
    assertTrue(bundled.startsWith(switchedOn), bundled)
  }
}

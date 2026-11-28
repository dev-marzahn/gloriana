package dev.marzahn.gloriana.ui.connections

import kotlin.test.Test
import kotlin.test.assertEquals

class OneDecimalTest {
  @Test
  fun secondsShowOneRoundedDecimal() {
    assertEquals("1.3", withOneDecimal(1.25))
    assertEquals("1.0", withOneDecimal(0.96))
    assertEquals("12.0", withOneDecimal(12.0))
  }

  @Test
  fun aNegativeValueKeepsItsSignOnTheWholePart() {
    assertEquals("-0.5", withOneDecimal(-0.5))
    assertEquals("0.0", withOneDecimal(-0.04))
  }
}

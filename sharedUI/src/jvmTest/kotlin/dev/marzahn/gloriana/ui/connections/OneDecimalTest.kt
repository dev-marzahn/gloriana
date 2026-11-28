package dev.marzahn.gloriana.ui.connections

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class OneDecimalTest {
  private fun <T> inLocale(locale: Locale, block: () -> T): T {
    val before = Locale.getDefault()
    Locale.setDefault(locale)
    return try {
      block()
    } finally {
      Locale.setDefault(before)
    }
  }

  @Test
  fun secondsShowOneRoundedDecimalWithTheLocalesSeparator() {
    assertEquals("1.3", inLocale(Locale.UK) { withOneDecimal(1.25) })
    assertEquals("1,3", inLocale(Locale.GERMANY) { withOneDecimal(1.25) })
    assertEquals("12.0", inLocale(Locale.UK) { withOneDecimal(12.0) })
  }
}

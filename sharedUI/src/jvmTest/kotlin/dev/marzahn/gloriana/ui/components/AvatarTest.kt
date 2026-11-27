package dev.marzahn.gloriana.ui.components

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarTest {
  private val english = Locale("en-US")

  @Test
  fun initialIsTheFirstLetterOrDigitUppercased() {
    assertEquals("M", avatarInitial("marguerite", english))
    assertEquals("7", avatarInitial("7th Sea", english))
    assertEquals("O", avatarInitial("  oren", english))
  }

  @Test
  fun initialKeepsCombiningMarksOfItsGrapheme() {
    assertEquals("É", avatarInitial("élise", english))
  }

  @Test
  fun initialUppercasesForTheLocale() {
    assertEquals("İ", avatarInitial("ilkay", Locale("tr-TR")))
  }

  @Test
  fun noInitialForAnEmptyNameOrASymbolOrEmoji() {
    assertNull(avatarInitial("", english))
    assertNull(avatarInitial("   ", english))
    assertNull(avatarInitial("★ Star", english))
    assertNull(avatarInitial("🐉 Dragon", english))
  }
}

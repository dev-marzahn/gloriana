package dev.marzahn.gloriana.ui.components

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

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

  @Test
  fun gradientIndexIsPinnedToTheId() {
    assertEquals(0, avatarGradientIndex(Uuid.fromLongs(0, 0), 6))
    assertEquals(1, avatarGradientIndex(Uuid.fromLongs(0, 1), 6))
    assertEquals(5, avatarGradientIndex(Uuid.fromLongs(0, 5), 6))
    assertEquals(0, avatarGradientIndex(Uuid.fromLongs(0, 6), 6))
    assertEquals(1, avatarGradientIndex(Uuid.parse("01890a5d-ac96-774b-bcce-b302099a8057"), 6))
  }
}

package dev.marzahn.gloriana.ui.characters.fields

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeBlockStyleTest {
  @Test
  fun `a code block is sized in sp from the reading size, never in em that Android resolves against its own default`() {
    val style = codeBlockStyle(TextStyle(fontSize = 16.sp, lineHeight = 25.sp))
    assertTrue(style.fontSize.isSp)
    assertEquals(14.4f, style.fontSize.value, absoluteTolerance = 0.001f)
    assertEquals(25.sp, style.lineHeight)
    assertEquals(FontFamily.Monospace, style.fontFamily)
  }
}

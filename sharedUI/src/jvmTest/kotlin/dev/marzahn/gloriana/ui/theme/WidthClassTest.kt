package dev.marzahn.gloriana.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WidthClassTest {
  @Test
  fun widthClassesBreakAtMaterialsBreakpoints() {
    assertEquals(WidthClass.Compact, WidthClass.of(0.dp))
    assertEquals(WidthClass.Compact, WidthClass.of(599.9.dp))
    assertEquals(WidthClass.Medium, WidthClass.of(600.dp))
    assertEquals(WidthClass.Medium, WidthClass.of(839.9.dp))
    assertEquals(WidthClass.Expanded, WidthClass.of(840.dp))
    assertEquals(WidthClass.Expanded, WidthClass.of(1280.dp))
  }
}

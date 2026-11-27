package dev.marzahn.gloriana.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
class LookShapes(base: Dp) {
  val panelRadius: Dp = base
  val controlRadius: Dp = base * CONTROL_SHARE
  val tagRadius: Dp = base * TAG_SHARE

  val panel = RoundedCornerShape(panelRadius)
  val control = RoundedCornerShape(controlRadius)
  val tag = RoundedCornerShape(tagRadius)
  val pill = CircleShape
  val sheet = RoundedCornerShape(topStart = panelRadius, topEnd = panelRadius)

  val material = Shapes(extraSmall = tag, small = control, medium = control, large = panel, extraLarge = panel)

  private companion object {
    const val CONTROL_SHARE = 0.625f
    const val TAG_SHARE = 0.25f
  }
}

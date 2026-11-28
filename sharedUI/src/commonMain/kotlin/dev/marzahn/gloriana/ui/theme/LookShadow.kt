package dev.marzahn.gloriana.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Immutable
data class LookShadow(val elevation: Dp, val color: Color)

@Immutable
data class LookShadows(val soft: LookShadow, val elevated: LookShadow)

fun Modifier.lookShadow(shadow: LookShadow, shape: Shape): Modifier =
  shadow(shadow.elevation, shape, clip = false, ambientColor = shadow.color, spotColor = shadow.color)

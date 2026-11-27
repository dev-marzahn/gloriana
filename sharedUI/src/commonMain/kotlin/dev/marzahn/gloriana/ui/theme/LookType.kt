package dev.marzahn.gloriana.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class LookType(val material: Typography, val message: TextStyle, val reading: TextStyle)

private enum class Family { Display, Ui }

private class Metrics(val size: TextUnit, val line: TextUnit)

private class Slot(
  val family: Family,
  val weight: FontWeight,
  val touch: Metrics,
  val dense: Metrics = touch,
  val letterSpacing: TextUnit = 0.sp,
) {
  fun style(fonts: LookFonts, pointerDense: Boolean): TextStyle {
    val metrics = if (pointerDense) dense else touch
    return TextStyle(
      fontFamily = if (family == Family.Display) fonts.display else fonts.ui,
      fontWeight = weight,
      fontSize = metrics.size,
      lineHeight = metrics.line,
      letterSpacing = letterSpacing,
    )
  }
}

private fun literata(size: Int, line: Int, letterSpacing: TextUnit = 0.sp) =
  Slot(Family.Display, FontWeight.Normal, Metrics(size.sp, line.sp), letterSpacing = letterSpacing)

private fun ui(weight: FontWeight, touch: Metrics, dense: Metrics = touch) = Slot(Family.Ui, weight, touch, dense)

private val Touch16 = Metrics(size = 16.sp, line = 24.sp)
private val Touch14 = Metrics(size = 14.sp, line = 20.sp)
private val Size12 = Metrics(size = 12.sp, line = 16.sp)
private val Dense13 = Metrics(size = 13.sp, line = 18.sp)
private val Dense11 = Metrics(size = 11.sp, line = 14.sp)

private val DisplayLarge = literata(size = 57, line = 64, letterSpacing = (-0.25).sp)
private val DisplayMedium = literata(size = 45, line = 52)
private val DisplaySmall = literata(size = 36, line = 44)
private val HeadlineLarge = literata(size = 30, line = 36, letterSpacing = (-0.02).em)
private val HeadlineMedium = literata(size = 28, line = 34, letterSpacing = (-0.02).em)
private val HeadlineSmall = literata(size = 24, line = 32, letterSpacing = (-0.015).em)
private val TitleLarge = literata(size = 20, line = 28, letterSpacing = (-0.01).em)
private val TitleMedium = ui(FontWeight.Medium, touch = Touch16, dense = Dense13)
private val TitleSmall = ui(FontWeight.Medium, touch = Touch14, dense = Dense13)
private val BodyLarge = ui(FontWeight.Normal, touch = Touch16, dense = Dense13)
private val BodyMedium = ui(FontWeight.Normal, touch = Touch14, dense = Size12)
private val BodySmall = ui(FontWeight.Normal, touch = Size12, dense = Dense11)
private val LabelLarge = ui(FontWeight.Medium, touch = Touch14, dense = Dense13)
private val LabelMedium = ui(FontWeight.Medium, touch = Size12)
private val LabelSmall = ui(FontWeight.Normal, touch = Size12, dense = Dense11)
private val Message = literata(size = 17, line = 27)
private val Reading = literata(size = 16, line = 25)

fun lookType(fonts: LookFonts, widthClass: WidthClass): LookType {
  val pointerDense = widthClass.pointerDense
  fun Slot.resolve() = style(fonts, pointerDense)
  return LookType(
    material = Typography(
      displayLarge = DisplayLarge.resolve(),
      displayMedium = DisplayMedium.resolve(),
      displaySmall = DisplaySmall.resolve(),
      headlineLarge = HeadlineLarge.resolve(),
      headlineMedium = HeadlineMedium.resolve(),
      headlineSmall = HeadlineSmall.resolve(),
      titleLarge = TitleLarge.resolve(),
      titleMedium = TitleMedium.resolve(),
      titleSmall = TitleSmall.resolve(),
      bodyLarge = BodyLarge.resolve(),
      bodyMedium = BodyMedium.resolve(),
      bodySmall = BodySmall.resolve(),
      labelLarge = LabelLarge.resolve(),
      labelMedium = LabelMedium.resolve(),
      labelSmall = LabelSmall.resolve(),
    ),
    message = Message.resolve(),
    reading = Reading.resolve(),
  )
}

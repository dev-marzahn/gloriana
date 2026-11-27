package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.brand.AppMarkCrown
import dev.marzahn.gloriana.logic.brand.AvatarGradientColors
import dev.marzahn.gloriana.logic.brand.AvatarGradientTable
import dev.marzahn.gloriana.logic.brand.bounds
import dev.marzahn.gloriana.logic.characters.files.PlaceholderAvatar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderAvatarTest {
  private val gradient = AvatarGradientTable[3]
  private val image = render(gradient)
  private val crownBox = crownBoxBySpec()

  @Test
  fun itIs768By1024() {
    assertEquals(WIDTH to HEIGHT, image.width to image.height)
  }

  @Test
  fun everyPixelOutsideTheCrownFollowsTheGradientAt135Degrees() {
    for (y in 0 until HEIGHT step SAMPLE_STEP) {
      for (x in 0 until WIDTH step SAMPLE_STEP) {
        if (!crownBox.contains(x, y)) assertNear(gradient.at135Degrees(x, y), image.rgbAt(x, y))
      }
    }
  }

  @Test
  fun aDiagonalAt45DegreesHasOneColourSoTheGradientRunsAt135NotCornerToCorner() {
    val onOneDiagonal = listOf(767 to 0, 0 to 767, 383 to 384).map { (x, y) -> image.rgbAt(x, y) }
    assertEquals(1, onOneDiagonal.toSet().size, onOneDiagonal.joinToString { "#%06X".format(it) })
  }

  @Test
  fun insideItsBoxTheCrownOnlyBlendsTheGradientTowardWhite() {
    for (y in crownBox.rows) {
      for (x in crownBox.columns) {
        val expected = gradient.at135Degrees(x, y)
        val actual = image.rgbAt(x, y)
        assertTrue(CHANNELS.all { actual.channel(it) >= expected.channel(it) - 1 }, "($x, $y) is darker")
      }
    }
  }

  @Test
  fun theWhiteCrownFillsItsCentredBoxAtThirtyPercentOfTheWidth() {
    assertEquals(WHITE, image.rgbAt(WIDTH / 2, HEIGHT / 2))
    val lit = crownBox.rows.flatMap { y -> crownBox.columns.filter { x -> image.isLit(x, y) }.map { it to y } }
    assertTrue(abs(lit.minOf { it.first } - crownBox.columns.first) <= 2, "crown starts late")
    assertTrue(abs(lit.maxOf { it.first } - crownBox.columns.last) <= 2, "crown ends early")
    assertTrue(abs(lit.minOf { it.second } - crownBox.rows.first) <= 2, "crown's top is low")
    assertTrue(abs(lit.maxOf { it.second } - crownBox.rows.last) <= 2, "crown's bottom is high")
  }

  @Test
  fun eachGradientOfTheTableStartsAndEndsTheGradient() {
    AvatarGradientTable.forEach { colors ->
      val picture = render(colors)
      assertNear(colors.startRgb, picture.rgbAt(0, 0))
      assertNear(colors.endRgb, picture.rgbAt(WIDTH - 1, HEIGHT - 1))
    }
  }

  private class Box(val columns: IntRange, val rows: IntRange) {
    fun contains(x: Int, y: Int) = x in columns && y in rows
  }

  private fun crownBoxBySpec(): Box {
    val unit = AppMarkCrown.shapes.flatten().bounds()
    val scale = WIDTH * CROWN_WIDTH_SHARE / unit.width
    val left = (WIDTH - unit.width * scale) / 2
    val top = (HEIGHT - unit.height * scale) / 2
    val columns = floor(left).toInt()..ceil(left + unit.width * scale).toInt()
    return Box(columns, floor(top).toInt()..ceil(top + unit.height * scale).toInt())
  }

  private fun render(colors: AvatarGradientColors): BufferedImage =
    ImageIO.read(PlaceholderAvatar.png(colors).inputStream())

  private fun AvatarGradientColors.at135Degrees(x: Int, y: Int): Int {
    val along = (x + y + 1f) / (WIDTH + HEIGHT)
    return CHANNELS.sumOf { shift ->
      val start = startRgb.channel(shift)
      (start + (endRgb.channel(shift) - start) * along).roundToInt() shl shift
    }
  }

  private fun Int.channel(shift: Int) = this shr shift and 0xFF

  private fun BufferedImage.rgbAt(x: Int, y: Int) = getRGB(x, y) and WHITE

  private fun BufferedImage.isLit(x: Int, y: Int): Boolean {
    val expected = gradient.at135Degrees(x, y)
    return CHANNELS.all { rgbAt(x, y).channel(it) - expected.channel(it) > LIT_ABOVE_GRADIENT }
  }

  private fun assertNear(expected: Int, actual: Int) {
    val near = CHANNELS.all { abs(expected.channel(it) - actual.channel(it)) <= 1 }
    assertTrue(near, "expected #%06X, got #%06X".format(expected, actual))
  }

  private companion object {
    const val WIDTH = 768
    const val HEIGHT = 1024
    const val CROWN_WIDTH_SHARE = 0.3f
    const val WHITE = 0xFFFFFF
    const val SAMPLE_STEP = 7
    const val LIT_ABOVE_GRADIENT = 8
    val CHANNELS = listOf(16, 8, 0)
  }
}

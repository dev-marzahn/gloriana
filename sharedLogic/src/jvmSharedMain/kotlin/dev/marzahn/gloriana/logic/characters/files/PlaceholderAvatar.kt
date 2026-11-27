package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.brand.AppMarkCrown
import dev.marzahn.gloriana.logic.brand.AvatarGradientColors
import dev.marzahn.gloriana.logic.brand.OutlinePoint
import dev.marzahn.gloriana.logic.brand.bounds
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.DeflaterOutputStream
import kotlin.math.roundToInt

private const val WIDTH = 768
private const val HEIGHT = 1024
private const val CROWN_WIDTH_SHARE = 0.3f
private const val BIT_DEPTH = 8
private const val TRUECOLOR = 2
private const val CHANNELS = 3
private const val SAMPLES_PER_AXIS = 4
private const val WHITE = 0xFFFFFF
private const val CHANNEL_MAX = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

internal object PlaceholderAvatar {
  fun png(gradient: AvatarGradientColors): ByteArray {
    val crown = CentredCrown()
    val pixels = ByteArrayOutputStream().also { bytes ->
      DeflaterOutputStream(bytes).use { deflated ->
        val row = ByteArray(1 + WIDTH * CHANNELS)
        repeat(HEIGHT) { y ->
          repeat(WIDTH) { x ->
            val rgb = blend(gradientAt(gradient, x, y), WHITE, crown.coverage(x, y))
            val offset = 1 + x * CHANNELS
            row[offset] = (rgb shr RED_SHIFT).toByte()
            row[offset + 1] = (rgb shr GREEN_SHIFT).toByte()
            row[offset + 2] = rgb.toByte()
          }
          deflated.write(row)
        }
      }
    }.toByteArray()
    return ByteArrayOutputStream().also { png ->
      png.write(PNG_SIGNATURE)
      png.writePngChunk("IHDR", header())
      png.writePngChunk("IDAT", pixels)
      png.writePngChunk(END_CHUNK, ByteArray(0))
    }.toByteArray()
  }

  private fun header(): ByteArray = ByteArrayOutputStream().also { bytes ->
    DataOutputStream(bytes).apply {
      writeInt(WIDTH)
      writeInt(HEIGHT)
      writeByte(BIT_DEPTH)
      writeByte(TRUECOLOR)
      writeByte(0)
      writeByte(0)
      writeByte(0)
    }
  }.toByteArray()

  private fun gradientAt(gradient: AvatarGradientColors, x: Int, y: Int): Int {
    val along135Degrees = (x + 0.5f + y + 0.5f) / (WIDTH + HEIGHT)
    return blend(gradient.startRgb, gradient.endRgb, along135Degrees)
  }

  private fun blend(from: Int, to: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
      val start = from shr shift and CHANNEL_MAX
      val end = to shr shift and CHANNEL_MAX
      return (start + (end - start) * amount).roundToInt() shl shift
    }
    return channel(RED_SHIFT) or channel(GREEN_SHIFT) or channel(0)
  }
}

private class CentredCrown {
  private val outline: List<List<OutlinePoint>>
  private val coveredColumns: IntRange
  private val coveredRows: IntRange

  init {
    val unit = AppMarkCrown.shapes.flatten().bounds()
    val scale = WIDTH * CROWN_WIDTH_SHARE / unit.width
    val left = (WIDTH - unit.width * scale) / 2 - unit.left * scale
    val top = (HEIGHT - unit.height * scale) / 2 - unit.top * scale
    outline = AppMarkCrown.shapes.map { shape -> shape.map { OutlinePoint(left + it.x * scale, top + it.y * scale) } }
    val placed = outline.flatten().bounds()
    coveredColumns = placed.left.toInt()..placed.right.toInt()
    coveredRows = placed.top.toInt()..placed.bottom.toInt()
  }

  fun coverage(x: Int, y: Int): Float {
    if (x !in coveredColumns || y !in coveredRows) return 0f
    var inside = 0
    repeat(SAMPLES_PER_AXIS) { row ->
      repeat(SAMPLES_PER_AXIS) { column ->
        val sample = OutlinePoint(x + (column + 0.5f) / SAMPLES_PER_AXIS, y + (row + 0.5f) / SAMPLES_PER_AXIS)
        if (outline.any { it.encloses(sample) }) inside++
      }
    }
    return inside.toFloat() / (SAMPLES_PER_AXIS * SAMPLES_PER_AXIS)
  }

  private fun List<OutlinePoint>.encloses(point: OutlinePoint): Boolean {
    var inside = false
    indices.forEach { index ->
      val a = this[index]
      val b = this[(index + 1) % size]
      val crosses = (a.y > point.y) != (b.y > point.y)
      if (crosses && point.x < a.x + (point.y - a.y) / (b.y - a.y) * (b.x - a.x)) inside = !inside
    }
    return inside
  }
}

package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.characters.files.AnimatedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarImageTest {
  @Test
  fun aGifWithTwoFramesIsAnimatedAndOneWithOneIsNot() {
    assertTrue(AnimatedImage.isAnimated(gif(frames = 2)))
    assertFalse(AnimatedImage.isAnimated(gif(frames = 1)))
  }

  @Test
  fun aWebpIsAnimatedWhenItsExtendedHeaderSaysSo() {
    assertTrue(AnimatedImage.isAnimated(webp(flags = 0x02)))
    assertFalse(AnimatedImage.isAnimated(webp(flags = 0x10)))
  }

  @Test
  fun downsamplingHalvesUntilTheImageFitsTheLimit() {
    assertEquals(1, powerOfTwoDownsampling(4096, 4096, maxPixels = 16L * 1024 * 1024))
    assertEquals(2, powerOfTwoDownsampling(4097, 4096, maxPixels = 16L * 1024 * 1024))
    assertEquals(4, powerOfTwoDownsampling(10_000, 10_000, maxPixels = 16L * 1024 * 1024))
  }

  private fun gif(frames: Int): ByteArray {
    val header = "GIF89a".toByteArray() + byteArrayOf(1, 0, 1, 0, 0x80.toByte(), 0, 0) + ByteArray(6)
    val graphicControl = byteArrayOf(0x21, 0xF9.toByte(), 4, 0, 0, 0, 0, 0)
    val image = byteArrayOf(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4C, 0x01, 0)
    return header + (1..frames).fold(ByteArray(0)) { all, _ -> all + graphicControl + image } + byteArrayOf(0x3B)
  }

  private fun webp(flags: Int) = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8X".toByteArray() + ByteArray(4) +
    byteArrayOf(flags.toByte()) + ByteArray(9)
}

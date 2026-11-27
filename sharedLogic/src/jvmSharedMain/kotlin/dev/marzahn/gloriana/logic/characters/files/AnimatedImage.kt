package dev.marzahn.gloriana.logic.characters.files

private const val GIF_HEADER_LENGTH = 6
private const val GIF_SCREEN_DESCRIPTOR_LENGTH = 7
private const val GIF_SCREEN_FLAGS_OFFSET = GIF_HEADER_LENGTH + 4
private const val GIF_IMAGE_DESCRIPTOR_LENGTH = 9
private const val GIF_EXTENSION = 0x21
private const val GIF_IMAGE = 0x2C
private const val GIF_COLOR_TABLE_FLAG = 0x80
private const val GIF_COLOR_TABLE_SIZE_MASK = 0x07
private const val GIF_COLOR_BYTES = 3
private const val RIFF_FORM_OFFSET = 8
private const val WEBP_FLAGS_OFFSET = 20
private const val WEBP_ANIMATION_FLAG = 0x02

internal object AnimatedImage {
  fun isAnimated(start: ByteArray): Boolean = when {
    start.startsWithAscii("GIF8") -> gifFrameCount(start) > 1
    start.startsWithAscii("RIFF") && start.startsWithAscii("WEBPVP8X", offset = RIFF_FORM_OFFSET) ->
      start.size > WEBP_FLAGS_OFFSET && start[WEBP_FLAGS_OFFSET].toInt() and WEBP_ANIMATION_FLAG != 0
    else -> false
  }

  private fun gifFrameCount(gif: ByteArray): Int {
    val globalColorTable = gif.unsignedAt(GIF_SCREEN_FLAGS_OFFSET)?.let(::colorTableLength) ?: 0
    var position = GIF_HEADER_LENGTH + GIF_SCREEN_DESCRIPTOR_LENGTH + globalColorTable
    var frames = 0
    var block = gif.unsignedAt(position)
    while (block == GIF_EXTENSION || block == GIF_IMAGE) {
      position = if (block == GIF_EXTENSION) {
        skipSubBlocks(gif, position + 2)
      } else {
        frames++
        val flags = gif.unsignedAt(position + GIF_IMAGE_DESCRIPTOR_LENGTH) ?: 0
        skipSubBlocks(gif, position + GIF_IMAGE_DESCRIPTOR_LENGTH + 1 + colorTableLength(flags) + 1)
      }
      block = gif.unsignedAt(position)
    }
    return frames
  }

  private fun colorTableLength(flags: Int): Int {
    val colors = 1 shl ((flags and GIF_COLOR_TABLE_SIZE_MASK) + 1)
    return if (flags and GIF_COLOR_TABLE_FLAG == 0) 0 else GIF_COLOR_BYTES * colors
  }

  private fun skipSubBlocks(gif: ByteArray, start: Int): Int {
    var position = start
    var length = gif.unsignedAt(position)
    while (length != null && length != 0) {
      position += 1 + length
      length = gif.unsignedAt(position)
    }
    return position + 1
  }

  private fun ByteArray.unsignedAt(index: Int): Int? = getOrNull(index)?.toUByte()?.toInt()

  private fun ByteArray.startsWithAscii(ascii: String, offset: Int = 0) =
    size >= offset + ascii.length && ascii.indices.all { this[offset + it] == ascii[it].code.toByte() }
}

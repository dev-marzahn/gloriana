package dev.marzahn.gloriana.logic.characters.files

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.DeflaterOutputStream

private const val WIDTH = 512
private const val HEIGHT = 768
private const val BIT_DEPTH = 8
private const val TRUECOLOR = 2
private val PAPER = byteArrayOf(0xED.toByte(), 0xE6.toByte(), 0xDA.toByte())

internal object PlaceholderAvatar {
  val png: ByteArray by lazy(::render)

  private fun render(): ByteArray {
    val header = ByteArrayOutputStream().also { bytes ->
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
    val pixels = ByteArrayOutputStream().also { bytes ->
      DeflaterOutputStream(bytes).use { deflated ->
        val row = ByteArray(1 + WIDTH * PAPER.size) { index -> if (index == 0) 0 else PAPER[(index - 1) % PAPER.size] }
        repeat(HEIGHT) { deflated.write(row) }
      }
    }.toByteArray()
    return ByteArrayOutputStream().also { png ->
      png.write(PNG_SIGNATURE)
      png.writePngChunk("IHDR", header)
      png.writePngChunk("IDAT", pixels)
      png.writePngChunk(END_CHUNK, ByteArray(0))
    }.toByteArray()
  }
}

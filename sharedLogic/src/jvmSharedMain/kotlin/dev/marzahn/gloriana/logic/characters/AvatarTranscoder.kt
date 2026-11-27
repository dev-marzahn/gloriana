package dev.marzahn.gloriana.logic.characters

import java.io.InputStream

fun interface AvatarTranscoder {
  fun toPng(image: () -> InputStream, maxPixels: Long): TranscodedAvatar?
}

class TranscodedAvatar(val png: ByteArray, val downsampled: Boolean)

fun powerOfTwoDownsampling(width: Int, height: Int, maxPixels: Long): Int {
  var factor = 1
  while ((width.toLong() / factor) * (height.toLong() / factor) > maxPixels) factor *= 2
  return factor
}

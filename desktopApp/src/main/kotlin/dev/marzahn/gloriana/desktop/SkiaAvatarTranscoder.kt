package dev.marzahn.gloriana.desktop

import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.characters.TranscodedAvatar
import dev.marzahn.gloriana.logic.characters.powerOfTwoDownsampling
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

private const val SKIA_ENCODED_LIMIT = 64L * 1024 * 1024

class SkiaAvatarTranscoder : AvatarTranscoder {
  override fun toPng(image: () -> InputStream, maxPixels: Long): TranscodedAvatar? =
    subsampledWithImageIo(image, maxPixels) ?: decodedWithSkia(image, maxPixels)

  private fun subsampledWithImageIo(image: () -> InputStream, maxPixels: Long): TranscodedAvatar? =
    image().use { input ->
      ImageIO.createImageInputStream(input)?.use { stream ->
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@use null
        try {
          reader.input = stream
          val factor = powerOfTwoDownsampling(reader.getWidth(0), reader.getHeight(0), maxPixels)
          val param = reader.defaultReadParam.apply { setSourceSubsampling(factor, factor, 0, 0) }
          val decoded = reader.read(0, param)
          val png = ByteArrayOutputStream().also { ImageIO.write(decoded, "png", it) }.toByteArray()
          TranscodedAvatar(png, downsampled = factor > 1)
        } finally {
          reader.dispose()
        }
      }
    }

  private fun decodedWithSkia(image: () -> InputStream, maxPixels: Long): TranscodedAvatar? {
    val bytes = image().use { it.readNBytes((SKIA_ENCODED_LIMIT + 1).toInt()) }
    if (bytes.size > SKIA_ENCODED_LIMIT) return null
    return Data.makeFromBytes(bytes).use { data ->
      val fits = runCatching { Codec.makeFromData(data).use { it.width.toLong() * it.height <= maxPixels } }
      if (fits.getOrDefault(false)) {
        Image.makeFromEncoded(bytes).use { decoded ->
          decoded.encodeToData(EncodedImageFormat.PNG)?.use { TranscodedAvatar(it.bytes, downsampled = false) }
        }
      } else {
        null
      }
    }
  }
}

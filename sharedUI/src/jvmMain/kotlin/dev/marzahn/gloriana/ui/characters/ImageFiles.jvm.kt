package dev.marzahn.gloriana.ui.characters

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

actual fun decodeImageFile(path: String, maxSidePx: Int): ImageBitmap? = try {
  val image = Image.makeFromEncoded(File(path).readBytes())
  try {
    scaledDown(image, maxSidePx).asComposeImageBitmap()
  } finally {
    image.close()
  }
} catch (_: IOException) {
  null
} catch (_: IllegalArgumentException) {
  null
}

private fun scaledDown(image: Image, maxSidePx: Int): Bitmap {
  val scale = minOf(1f, maxSidePx.toFloat() / maxOf(image.width, image.height))
  val width = (image.width * scale).roundToInt().coerceAtLeast(1)
  val height = (image.height * scale).roundToInt().coerceAtLeast(1)
  val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
  Canvas(bitmap).use { canvas ->
    canvas.drawImageRect(
      image,
      Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
      Rect.makeWH(width.toFloat(), height.toFloat()),
      SamplingMode.LINEAR,
      null,
      true,
    )
  }
  return bitmap.apply { setImmutable() }
}

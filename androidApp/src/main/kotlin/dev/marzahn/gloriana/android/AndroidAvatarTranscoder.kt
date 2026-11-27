package dev.marzahn.gloriana.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.characters.TranscodedAvatar
import dev.marzahn.gloriana.logic.characters.powerOfTwoDownsampling
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val LOSSLESS = 100

class AndroidAvatarTranscoder : AvatarTranscoder {
  override fun toPng(image: () -> InputStream, maxPixels: Long): TranscodedAvatar? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    image().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val factor = powerOfTwoDownsampling(bounds.outWidth, bounds.outHeight, maxPixels)
    val sampled = BitmapFactory.Options().apply { inSampleSize = factor }
    return image().use { BitmapFactory.decodeStream(it, null, sampled) }?.let { bitmap ->
      val out = ByteArrayOutputStream()
      val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, LOSSLESS, out)
      bitmap.recycle()
      TranscodedAvatar(out.toByteArray(), downsampled = factor > 1).takeIf { compressed }
    }
  }
}

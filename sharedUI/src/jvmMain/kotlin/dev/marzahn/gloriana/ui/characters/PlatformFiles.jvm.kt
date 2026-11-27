package dev.marzahn.gloriana.ui.characters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.DesktopExportDestination
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.characters.PickedFile
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToInt

@Composable
actual fun rememberCardFilePicker(onPick: (List<PickedFile>) -> Unit): CardFilePicker {
  val latest by rememberUpdatedState(onPick)
  return remember {
    CardFilePicker {
      val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(".png, .json, .charx", "png", "json", "charx")
        isAcceptAllFileFilterUsed = true
      }
      if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        latest(chooser.selectedFiles.map { DesktopPickedFile(it.toPath()) })
      }
    }
  }
}

@Composable
actual fun rememberCardSaver(onChoose: (SaveTarget) -> Unit): CardSaver {
  val latest by rememberUpdatedState(onChoose)
  return remember {
    CardSaver { proposedName, _ ->
      val dialog = FileDialog(null as Frame?, null, FileDialog.SAVE).apply { file = proposedName }
      dialog.isVisible = true
      val name = dialog.file
      val directory = dialog.directory
      if (name != null && directory != null) {
        latest(SaveTarget(DesktopExportDestination(File(directory, name).toPath()), name))
      }
    }
  }
}

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

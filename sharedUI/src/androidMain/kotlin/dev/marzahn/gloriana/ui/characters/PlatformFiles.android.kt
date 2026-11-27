package dev.marzahn.gloriana.ui.characters

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.marzahn.gloriana.logic.characters.AndroidExportDestination
import dev.marzahn.gloriana.logic.characters.AndroidPickedFile
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.PickedFile

private const val ANY_FILE = "*/*"
private const val PNG_TYPE = "image/png"
private const val JSON_TYPE = "application/json"

@Composable
actual fun rememberCardFilePicker(onPick: (List<PickedFile>) -> Unit): CardFilePicker {
  val resolver = LocalContext.current.contentResolver
  val latest by rememberUpdatedState(onPick)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    latest(uris.map { AndroidPickedFile(resolver, it) })
  }
  return remember(launcher) { CardFilePicker { launcher.launch(arrayOf(ANY_FILE)) } }
}

@Composable
actual fun rememberCardSaver(onChoose: (SaveTarget) -> Unit): CardSaver {
  val resolver = LocalContext.current.contentResolver
  val latest by rememberUpdatedState(onChoose)
  var proposed by rememberSaveable { mutableStateOf("") }
  val chosen = { uri: Uri? ->
    uri?.let { latest(SaveTarget(AndroidExportDestination(resolver, it), resolver.nameOf(it, proposed))) }
  }
  val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PNG_TYPE)) { chosen(it) }
  val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_TYPE)) { chosen(it) }
  return remember(png, json) {
    CardSaver { proposedName, format ->
      proposed = proposedName
      if (format == CardFormat.Png) png.launch(proposedName) else json.launch(proposedName)
    }
  }
}

private fun ContentResolver.nameOf(uri: Uri, fallback: String): String = runCatching {
  query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
  }
}.getOrNull() ?: fallback

actual fun decodeImageFile(path: String, maxSidePx: Int): ImageBitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(path, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  var sample = 1
  while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
  val options = BitmapFactory.Options().apply { inSampleSize = sample }
  return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

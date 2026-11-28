package dev.marzahn.gloriana.ui.files

import android.content.ContentResolver
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
import androidx.compose.ui.platform.LocalContext
import dev.marzahn.gloriana.logic.characters.AndroidExportDestination
import dev.marzahn.gloriana.logic.characters.AndroidPickedFile
import dev.marzahn.gloriana.logic.files.PickedFile

private const val ANY_FILE = "*/*"

@Composable
actual fun rememberFilePicker(choice: FileChoice, onPick: (List<PickedFile>) -> Unit): FilePicker {
  val resolver = LocalContext.current.contentResolver
  val latest by rememberUpdatedState(onPick)
  val several = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    latest(uris.map { AndroidPickedFile(resolver, it) })
  }
  val one = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { latest(listOf(AndroidPickedFile(resolver, it))) }
  }
  return remember(choice, several, one) {
    FilePicker { if (choice.several) several.launch(arrayOf(ANY_FILE)) else one.launch(arrayOf(ANY_FILE)) }
  }
}

@Composable
actual fun rememberFileSaver(onChoose: (SaveTarget) -> Unit): FileSaver {
  val resolver = LocalContext.current.contentResolver
  val latest by rememberUpdatedState(onChoose)
  var proposed by rememberSaveable { mutableStateOf("") }
  val chosen = { uri: Uri? ->
    uri?.let { latest(SaveTarget(AndroidExportDestination(resolver, it), resolver.nameOf(it, proposed))) }
  }
  val launchers = SavedFileType.entries.associateWith { type ->
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(type.mimeType)) { chosen(it) }
  }
  return remember(launchers) {
    FileSaver { proposedName, type ->
      proposed = proposedName
      launchers.getValue(type).launch(proposedName)
    }
  }
}

private fun ContentResolver.nameOf(uri: Uri, fallback: String): String = runCatching {
  query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
  }
}.getOrNull() ?: fallback

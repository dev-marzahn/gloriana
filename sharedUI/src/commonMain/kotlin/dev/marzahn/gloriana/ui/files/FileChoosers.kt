package dev.marzahn.gloriana.ui.files

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.PickedFile

class FileChoice(val extensions: List<String>, val several: Boolean)

fun interface FilePicker {
  fun pick()
}

enum class SavedFileType(val extension: String, val mimeType: String) {
  Png("png", "image/png"),
  Json("json", "application/json"),
}

class SaveTarget(val destination: ExportDestination, val fileName: String)

fun interface FileSaver {
  fun choose(proposedName: String, type: SavedFileType)
}

@Composable
expect fun rememberFilePicker(choice: FileChoice, onPick: (List<PickedFile>) -> Unit): FilePicker

@Composable
expect fun rememberFileSaver(onChoose: (SaveTarget) -> Unit): FileSaver

private val UnsafeFileNameCharacters = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

fun proposedFileName(name: String, type: SavedFileType): String {
  val base = UnsafeFileNameCharacters.replace(name, "_").trim().ifEmpty { "_" }
  return "$base.${type.extension}"
}

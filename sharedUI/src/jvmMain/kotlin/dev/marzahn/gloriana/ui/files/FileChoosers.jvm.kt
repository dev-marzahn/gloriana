package dev.marzahn.gloriana.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.marzahn.gloriana.logic.characters.DesktopExportDestination
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberFilePicker(choice: FileChoice, onPick: (List<PickedFile>) -> Unit): FilePicker {
  val latest by rememberUpdatedState(onPick)
  return remember(choice) {
    FilePicker {
      val extensions = choice.extensions.toTypedArray()
      val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = choice.several
        fileFilter = FileNameExtensionFilter(extensions.joinToString { ".$it" }, *extensions)
        isAcceptAllFileFilterUsed = true
      }
      if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val files = if (choice.several) chooser.selectedFiles.toList() else listOf(chooser.selectedFile)
        latest(files.map { DesktopPickedFile(it.toPath()) })
      }
    }
  }
}

@Composable
actual fun rememberFileSaver(onChoose: (SaveTarget) -> Unit): FileSaver {
  val latest by rememberUpdatedState(onChoose)
  return remember {
    FileSaver { proposedName, _ ->
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

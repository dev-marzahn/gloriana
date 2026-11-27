package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.characters.files.asByteSink
import dev.marzahn.gloriana.logic.characters.files.asByteSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import dev.marzahn.gloriana.logic.storage.writeAtomically

class DesktopPickedFile(private val path: Path) : PickedFile {
  override val name: String = path.fileName.toString()

  override fun open(): OpenedFile = try {
    OpenedFile.Opened(Files.newInputStream(path).asByteSource())
  } catch (_: IOException) {
    OpenedFile.Unavailable
  }
}

class DesktopExportDestination(private val path: Path) : ExportDestination {
  override fun write(content: (ByteSink) -> Unit): ExportWrite {
    return try {
      path.writeAtomically { content(it.asByteSink()) }
      ExportWrite.Written
    } catch (error: IOException) {
      error.asFailure()
    } catch (failure: ExportContentFailure) {
      ExportWrite.Failed(ExportProblem(failure.reason, incompleteFileLeft = false))
    }
  }

  private fun IOException.asFailure() = ExportWrite.Failed(
    ExportProblem(
      reason = if (isStorageFull()) ExportFailure.StorageFull else ExportFailure.CantWrite,
      incompleteFileLeft = false,
    ),
  )
}

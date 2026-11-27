package dev.marzahn.gloriana.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.export_failed
import dev.marzahn.gloriana.ui.resources.export_failed_cant_write
import dev.marzahn.gloriana.ui.resources.export_failed_storage
import dev.marzahn.gloriana.ui.resources.export_failed_unreadable
import dev.marzahn.gloriana.ui.resources.export_incomplete
import dev.marzahn.gloriana.ui.resources.export_incomplete_access
import dev.marzahn.gloriana.ui.resources.export_incomplete_storage
import org.jetbrains.compose.resources.stringResource

@Composable
fun exportNote(export: ExportState?): AnnotatedString? {
  val failed = export as? ExportState.NotSaved ?: return null
  val problem = failed.problem
  return if (problem.incompleteFileLeft) {
    val detail = when (problem.reason) {
      ExportFailure.StorageFull -> Res.string.export_incomplete_storage
      else -> Res.string.export_incomplete_access
    }
    boldThenPlain(stringResource(Res.string.export_incomplete, failed.fileName), stringResource(detail))
  } else {
    val detail = when (problem.reason) {
      ExportFailure.StorageFull -> Res.string.export_failed_storage
      ExportFailure.CantWrite -> Res.string.export_failed_cant_write
      ExportFailure.StoredFileUnreadable -> Res.string.export_failed_unreadable
    }
    boldThenPlain(stringResource(Res.string.export_failed, failed.fileName), stringResource(detail))
  }
}

package dev.marzahn.gloriana.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.components.Confirmation
import dev.marzahn.gloriana.ui.components.GlorianaConfirmation
import dev.marzahn.gloriana.ui.components.MenuItem
import dev.marzahn.gloriana.ui.components.NotDeletedNote
import dev.marzahn.gloriana.ui.components.NotMadeDefaultNote
import dev.marzahn.gloriana.ui.components.RefusedAsDefaultCopy
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.delete
import dev.marzahn.gloriana.ui.resources.delete_title
import dev.marzahn.gloriana.ui.resources.deleting
import dev.marzahn.gloriana.ui.resources.ic_delete
import dev.marzahn.gloriana.ui.resources.ic_star
import dev.marzahn.gloriana.ui.resources.make_default
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ItemWorkNotes(
  work: ItemWork,
  name: String,
  refused: RefusedAsDefaultCopy,
  onMakeDefault: () -> Unit,
  onAskToDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (work.notMadeDefault) NotMadeDefaultNote(name, onMakeDefault, modifier)
  work.notDeleted?.let { why -> NotDeletedNote(why, name, refused, onAskToDelete, modifier) }
}

@Composable
fun ItemDeleteQuestion(
  work: ItemWork,
  name: String,
  lines: @Composable (DeleteImpact) -> List<String>,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val impact = work.askedImpact ?: return
  val deleting = work.activity is ItemActivity.Deleting
  GlorianaConfirmation(
    Confirmation(
      title = stringResource(Res.string.delete_title, name),
      lines = lines(impact),
      confirmLabel = stringResource(if (deleting) Res.string.deleting else Res.string.delete),
    ),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    busy = deleting,
  )
}

class DeleteItemCopy(val label: StringResource, val defaultCantDelete: StringResource)

@Composable
fun makeDefaultMenuItem(isDefault: Boolean, onClick: () -> Unit): MenuItem? =
  MenuItem(stringResource(Res.string.make_default), Res.drawable.ic_star, onClick).takeUnless { isDefault }

@Composable
fun deleteMenuItem(copy: DeleteItemCopy, isDefault: Boolean, onClick: () -> Unit): MenuItem = MenuItem(
  stringResource(copy.label),
  Res.drawable.ic_delete,
  onClick = onClick,
  enabled = !isDefault,
  detail = if (isDefault) stringResource(copy.defaultCantDelete) else null,
)

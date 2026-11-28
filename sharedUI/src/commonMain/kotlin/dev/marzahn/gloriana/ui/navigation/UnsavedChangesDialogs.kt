package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.marzahn.gloriana.ui.components.Confirmation
import dev.marzahn.gloriana.ui.components.GlorianaConfirmation
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.discard_quit_more
import dev.marzahn.gloriana.ui.resources.discard_quit_two
import dev.marzahn.gloriana.ui.resources.discard_title
import dev.marzahn.gloriana.ui.resources.keep_editing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LeaveQuestion(navigator: ShellNavigator) {
  val pending = navigator.pendingLeave ?: return
  GlorianaConfirmation(
    discardConfirmation(pending.page.leavingMessage),
    onConfirm = pending.discardAndLeave,
    onDismiss = pending.keepEditing,
  )
}

@Composable
internal fun QuitQuestion(navigator: ShellNavigator, quitRequests: QuitRequests) {
  val request = quitRequests.pending ?: return
  val pages = navigator.unsavedPages
  if (pages.isEmpty()) {
    LaunchedEffect(request) { quitRequests.quit() }
    return
  }
  val names = pages.map { it.quittingName }
  val message = when (names.size) {
    1 -> stringResource(Res.string.changes_not_saved, names.single())
    2 -> stringResource(Res.string.discard_quit_two, names[0], names[1])
    else -> pluralStringResource(Res.plurals.discard_quit_more, names.size - 2, names[0], names[1], names.size - 2)
  }
  GlorianaConfirmation(
    discardConfirmation(message),
    onConfirm = {
      pages.forEach { it.discard() }
      quitRequests.quit()
    },
    onDismiss = quitRequests::stay,
  )
}

@Composable
private fun discardConfirmation(message: String) = Confirmation(
  title = stringResource(Res.string.discard_title),
  lines = listOf(message),
  confirmLabel = stringResource(Res.string.discard),
  cancelLabel = stringResource(Res.string.keep_editing),
)

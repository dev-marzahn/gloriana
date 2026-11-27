package dev.marzahn.gloriana.ui.connections

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PageSubject(private val context: PageContext, private val commands: ConnectionCommands) {
  private val session = context.session

  fun on(intent: PageIntent.SubjectIntent) {
    when (intent) {
      PageIntent.MakeDefault -> makeDefault()
      PageIntent.AskToDelete -> context.idlePage()?.let {
        session.update { current -> current.copy(activity = PageActivity.ConfirmingDelete, note = null) }
      }
      PageIntent.CancelDelete -> context.activity(PageActivity.Idle)
      PageIntent.ConfirmDelete -> confirmDelete()
    }
  }

  private fun makeDefault() {
    val connection = context.idlePage()?.saved ?: return
    context.activity(PageActivity.MakingDefault)
    context.scope.launch {
      val note = when (commands.makeDefault(connection)) {
        CommandResult.Done -> null
        CommandResult.Failed -> PageNote.NotMadeDefault
      }
      session.update { it.copy(activity = PageActivity.Idle, note = note) }
    }
  }

  private fun confirmDelete() {
    val connection = context.page()?.saved ?: return
    context.activity(PageActivity.Deleting)
    context.scope.launch {
      when (commands.delete(connection)) {
        CommandResult.Done -> Unit
        CommandResult.Failed -> session.update { it.copy(activity = PageActivity.Idle, note = PageNote.NotDeleted) }
      }
    }
  }
}

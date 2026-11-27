package dev.marzahn.gloriana.ui.settings

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.ui.connections.DeleteConnectionConfirmation
import dev.marzahn.gloriana.ui.connections.StartOverConfirmation

private class DeleteSheet(val row: ConnectionRowState, val deleting: Boolean)

@Composable
internal fun SettingsSheets(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
  val delete = when (val activity = state.rowActivity) {
    is RowActivity.ConfirmingDelete -> DeleteSheet(activity.row, deleting = false)
    is RowActivity.Deleting -> DeleteSheet(activity.row, deleting = true)
    RowActivity.Idle, is RowActivity.MakingDefault, is RowActivity.DeleteFailed, is RowActivity.NotMadeDefault -> null
  }
  delete?.let { sheet ->
    DeleteConnectionConfirmation(
      sheet.row.connection,
      sheet.row.isDefault,
      sheet.deleting,
      onConfirm = { onIntent(SettingsIntent.ConfirmDelete) },
      onDismiss = { onIntent(SettingsIntent.CancelDelete) },
    )
  }
  when (val keys = state.keys) {
    is KeysNote.Unreadable -> StartOverSheet(keys, onIntent)
    KeysNote.None -> Unit
  }
}

@Composable
private fun StartOverSheet(keys: KeysNote.Unreadable, onIntent: (SettingsIntent) -> Unit) {
  val erasing = when (keys.step) {
    StartOverStep.Confirming -> false
    StartOverStep.Erasing -> true
    StartOverStep.Idle, is StartOverStep.Failed -> return
  }
  StartOverConfirmation(
    connections = keys.holding,
    erasing = erasing,
    onConfirm = { onIntent(SettingsIntent.ConfirmStartOver) },
    onDismiss = { onIntent(SettingsIntent.CancelStartOver) },
  )
}

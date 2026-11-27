package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.dismiss
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.resources.ic_info_20
import dev.marzahn.gloriana.ui.resources.import_note_credentials
import dev.marzahn.gloriana.ui.resources.import_note_credentials_too
import dev.marzahn.gloriana.ui.resources.import_note_no_connection
import dev.marzahn.gloriana.ui.resources.samplers_imported_without
import dev.marzahn.gloriana.ui.resources.samplers_not_copied_connections_unreadable
import dev.marzahn.gloriana.ui.resources.samplers_not_copied_deleted
import dev.marzahn.gloriana.ui.resources.samplers_not_copied_other
import dev.marzahn.gloriana.ui.resources.samplers_not_copied_other_detail
import dev.marzahn.gloriana.ui.resources.samplers_not_copied_storage
import dev.marzahn.gloriana.ui.resources.samplers_not_copied_storage_detail
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val InfoIconSize = 20.dp

@Composable
fun ImportNotesView(notes: ImportNotes, presetName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val spacing = GlorianaTheme.spacing
  Column(modifier.padding(bottom = spacing.s8), verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
    notes.copyFailed?.let { failed -> NoteCard { NoteLine(failed.text(presetName)) } }
    if (notes.hasInfo) InfoNote(infoText(notes), onDismiss)
  }
}

@Composable
private fun CopyFailed.text(presetName: String) = when (this) {
  CopyFailed.ConnectionsUnreadable -> boldThenPlain(
    stringResource(Res.string.samplers_not_copied_connections_unreadable),
    stringResource(Res.string.samplers_imported_without, presetName),
  )
  is CopyFailed.OnConnection -> text(presetName)
}

@Composable
private fun CopyFailed.OnConnection.text(presetName: String) = when (failure) {
  CopyFailure.Storage -> boldThenPlain(
    stringResource(Res.string.samplers_not_copied_storage),
    stringResource(Res.string.samplers_not_copied_storage_detail, connectionName, presetName),
  )
  CopyFailure.ConnectionDeleted -> boldThenPlain(
    stringResource(Res.string.samplers_not_copied_deleted, connectionName),
    stringResource(Res.string.samplers_imported_without, presetName),
  )
  CopyFailure.Other -> boldThenPlain(
    stringResource(Res.string.samplers_not_copied_other),
    stringResource(Res.string.samplers_not_copied_other_detail, connectionName, presetName),
  )
}

@Composable
private fun infoText(notes: ImportNotes): String = when {
  notes.samplersDroppedNoConnection && notes.credentialsSkipped ->
    stringResource(Res.string.import_note_credentials_too, notes.fileName)
  notes.samplersDroppedNoConnection -> stringResource(Res.string.import_note_no_connection, notes.fileName)
  else -> stringResource(Res.string.import_note_credentials, notes.fileName)
}

@Composable
private fun InfoNote(text: String, onDismiss: () -> Unit) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.control
  Row(
    Modifier
      .fillMaxWidth()
      .background(colors.surface2, shape)
      .border(1.dp, colors.borderSubtle, shape)
      .padding(start = spacing.s12, top = spacing.s12, bottom = spacing.s12),
    horizontalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    Icon(painterResource(Res.drawable.ic_info_20), null, Modifier.size(InfoIconSize), tint = colors.textMuted)
    Text(text, Modifier.weight(1f), style = GlorianaTheme.type.material.bodyMedium, color = colors.textMuted)
    GlorianaIconButton(
      Res.drawable.ic_close,
      stringResource(Res.string.dismiss),
      onDismiss,
      Modifier.align(Alignment.Top),
    )
  }
}

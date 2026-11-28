package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.api.hostOf
import dev.marzahn.gloriana.logic.connection.heldSecrets
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.Confirmation
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaConfirmation
import dev.marzahn.gloriana.ui.components.GlorianaMoreMenu
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.components.Mark
import dev.marzahn.gloriana.ui.components.MenuItem
import dev.marzahn.gloriana.ui.components.RowTile
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.connection_default_mark
import dev.marzahn.gloriana.ui.resources.connection_more
import dev.marzahn.gloriana.ui.resources.create_connection
import dev.marzahn.gloriana.ui.resources.create_connection_text
import dev.marzahn.gloriana.ui.resources.delete
import dev.marzahn.gloriana.ui.resources.delete_connection
import dev.marzahn.gloriana.ui.resources.delete_connection_both
import dev.marzahn.gloriana.ui.resources.delete_connection_default
import dev.marzahn.gloriana.ui.resources.delete_connection_headers
import dev.marzahn.gloriana.ui.resources.delete_connection_key
import dev.marzahn.gloriana.ui.resources.delete_connection_title
import dev.marzahn.gloriana.ui.resources.deleting
import dev.marzahn.gloriana.ui.resources.ic_delete
import dev.marzahn.gloriana.ui.resources.ic_edit_note_20
import dev.marzahn.gloriana.ui.resources.ic_hub_20
import dev.marzahn.gloriana.ui.resources.ic_star
import dev.marzahn.gloriana.ui.resources.joined_by_dot
import dev.marzahn.gloriana.ui.resources.make_default
import dev.marzahn.gloriana.ui.resources.template_from_nothing
import dev.marzahn.gloriana.ui.resources.template_from_nothing_text
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import org.jetbrains.compose.resources.stringResource

private val TemplateRowHeight = 56.dp

@Composable
fun DeleteConnectionConfirmation(
  connection: Connection,
  isDefault: Boolean,
  deleting: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val name = connection.fields.name
  val held = when (connection.heldSecrets) {
    HeldSecrets.KeyAndHeaderValues -> Res.string.delete_connection_both
    HeldSecrets.Key -> Res.string.delete_connection_key
    HeldSecrets.HeaderValues -> Res.string.delete_connection_headers
    null -> null
  }
  val lines = listOfNotNull(
    held?.let { stringResource(it) },
    if (isDefault) stringResource(Res.string.delete_connection_default, name) else null,
  )
  GlorianaConfirmation(
    Confirmation(
      title = stringResource(Res.string.delete_connection_title, name),
      lines = lines,
      confirmLabel = stringResource(if (deleting) Res.string.deleting else Res.string.delete),
    ),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    busy = deleting,
  )
}

@Composable
fun CreateConnectionSheet(onPick: (NewConnectionStart) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  GlorianaSheet(onDismiss, modifier) {
    Text(stringResource(Res.string.create_connection), style = type.headlineSmall, color = colors.text)
    Text(
      stringResource(Res.string.create_connection_text),
      Modifier.padding(top = spacing.s8, bottom = spacing.s16),
      style = type.bodyMedium,
      color = colors.textMuted,
    )
    NewConnectionStart.choices.forEach { start -> TemplateRow(start) { onPick(start) } }
    if (GlorianaTheme.widthClass.sheetStyle == SheetStyle.Dialog) {
      SheetButtons(Modifier.padding(top = spacing.s16)) { buttonModifier ->
        GlorianaButton(stringResource(Res.string.cancel), onDismiss, buttonModifier, kind = ButtonKind.Ghost)
      }
    }
  }
}

@Composable
private fun TemplateRow(start: NewConnectionStart, onClick: () -> Unit) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val title: String
  val line: String
  when (start) {
    is NewConnectionStart.FromTemplate -> {
      title = start.template.templateName
      line = stringResource(
        Res.string.joined_by_dot,
        stringResource(start.template.format.label),
        hostOf(start.template.baseUrl),
      )
    }
    NewConnectionStart.FromNothing -> {
      title = stringResource(Res.string.template_from_nothing)
      line = stringResource(Res.string.template_from_nothing_text)
    }
  }
  val icon = if (start == NewConnectionStart.FromNothing) Res.drawable.ic_edit_note_20 else Res.drawable.ic_hub_20
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = TemplateRowHeight)
      .tappable(GlorianaTheme.shapes.control, onClick = onClick)
      .padding(horizontal = spacing.s8, vertical = spacing.s10),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s12),
  ) {
    RowTile(icon, ListDensity.Screen)
    Column {
      Text(title, style = type.titleMedium, color = colors.text)
      Text(line, style = type.bodyMedium, color = colors.textMuted)
    }
  }
}

@Composable
fun ConnectionMoreMenu(
  name: String,
  isDefault: Boolean,
  onMakeDefault: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val items = listOfNotNull(
    MenuItem(stringResource(Res.string.make_default), Res.drawable.ic_star, onMakeDefault).takeUnless { isDefault },
    MenuItem(stringResource(Res.string.delete_connection), Res.drawable.ic_delete, onDelete),
  )
  GlorianaMoreMenu(stringResource(Res.string.connection_more, name), items, modifier)
}

@Composable
fun DefaultMark(isDefault: Boolean, modifier: Modifier = Modifier) {
  val standard = GlorianaTheme.motion.standard
  AnimatedVisibility(
    isDefault,
    modifier,
    enter = fadeIn(standard.tween()) + expandHorizontally(standard.tween()),
    exit = fadeOut(standard.tween()) + shrinkHorizontally(standard.tween()),
  ) {
    Mark(stringResource(Res.string.connection_default_mark))
  }
}

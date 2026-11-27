package dev.marzahn.gloriana.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.characters.AlreadyInLibrary
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryAnswer
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryChoice
import dev.marzahn.gloriana.ui.characters.FocusFirstForKeyboard
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaCheckbox
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.already_copy
import dev.marzahn.gloriana.ui.resources.already_rest
import dev.marzahn.gloriana.ui.resources.already_text
import dev.marzahn.gloriana.ui.resources.already_text_json
import dev.marzahn.gloriana.ui.resources.already_text_json_lorebook
import dev.marzahn.gloriana.ui.resources.already_text_lorebook
import dev.marzahn.gloriana.ui.resources.already_title
import dev.marzahn.gloriana.ui.resources.already_update
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.create_import
import dev.marzahn.gloriana.ui.resources.create_import_text
import dev.marzahn.gloriana.ui.resources.create_title
import dev.marzahn.gloriana.ui.resources.create_write
import dev.marzahn.gloriana.ui.resources.create_write_text
import dev.marzahn.gloriana.ui.resources.ic_content_copy_20
import dev.marzahn.gloriana.ui.resources.ic_edit
import dev.marzahn.gloriana.ui.resources.ic_file_open
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import dev.marzahn.gloriana.ui.theme.disabled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val RowTileSize = 40.dp

@Composable
fun CreateSheet(onImport: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  GlorianaSheet(onDismiss, modifier) {
    SheetTitle(stringResource(Res.string.create_title))
    Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
      CreateRow(Res.drawable.ic_file_open, Res.string.create_import, Res.string.create_import_text, true, onImport)
      CreateRow(Res.drawable.ic_edit, Res.string.create_write, Res.string.create_write_text, false) {}
    }
  }
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    modifier.padding(bottom = GlorianaTheme.spacing.s16),
    style = GlorianaTheme.type.material.headlineSmall,
    color = GlorianaTheme.colors.text,
  )
}

@Composable
private fun CreateRow(
  icon: DrawableResource,
  title: StringResource,
  line: StringResource,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.control
  val tint: (Color) -> Color = { if (enabled) it else it.disabled() }
  Row(
    Modifier
      .fillMaxWidth()
      .tappable(shape, enabled = enabled, onClick = onClick)
      .padding(GlorianaTheme.spacing.s8),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s12),
  ) {
    Box(Modifier.size(RowTileSize).background(colors.surface2, shape), contentAlignment = Alignment.Center) {
      Icon(painterResource(icon), null, tint = tint(colors.textMuted))
    }
    Column(Modifier.weight(1f)) {
      Text(stringResource(title), style = GlorianaTheme.type.material.titleMedium, color = tint(colors.text))
      Text(stringResource(line), style = GlorianaTheme.type.material.bodyMedium, color = tint(colors.textMuted))
    }
  }
}

@Composable
fun AlreadyInLibrarySheet(
  question: AlreadyInLibrary,
  onAnswer: (AlreadyInLibraryAnswer) -> Unit,
  modifier: Modifier = Modifier,
) {
  var forTheRest by rememberSaveable(question) { mutableStateOf(false) }
  val answer = { choice: AlreadyInLibraryChoice -> onAnswer(AlreadyInLibraryAnswer(choice, forTheRest)) }
  val cancelFocus = remember { FocusRequester() }
  val name = question.existing.name
  GlorianaSheet({ answer(AlreadyInLibraryChoice.Cancel) }, modifier) {
    SheetTitle(stringResource(Res.string.already_title, name))
    Text(
      stringResource(question.message(), question.fileName),
      Modifier.padding(bottom = GlorianaTheme.spacing.s16),
      style = GlorianaTheme.type.material.bodyMedium,
      color = GlorianaTheme.colors.textMuted,
    )
    if (question.filesLeftAfterThis > 0) RestOfImportRow(forTheRest) { forTheRest = it }
    val copy = @Composable { buttonModifier: Modifier ->
      val label = stringResource(Res.string.already_copy)
      val icon = Res.drawable.ic_content_copy_20
      GlorianaButton(label, { answer(AlreadyInLibraryChoice.Copy) }, buttonModifier, icon = icon)
    }
    val update = @Composable { buttonModifier: Modifier ->
      val label = stringResource(Res.string.already_update, name)
      GlorianaButton(label, { answer(AlreadyInLibraryChoice.Update) }, buttonModifier, kind = ButtonKind.Danger)
    }
    val cancel = @Composable { buttonModifier: Modifier ->
      val label = stringResource(Res.string.cancel)
      val focused = buttonModifier.focusRequester(cancelFocus)
      GlorianaButton(label, { answer(AlreadyInLibraryChoice.Cancel) }, focused, kind = ButtonKind.Ghost)
    }
    SheetButtons(listOf(copy, update, cancel), listOf(cancel, copy, update))
    FocusFirstForKeyboard(cancelFocus)
  }
}

private fun AlreadyInLibrary.message() = when {
  fileHoldsNoFiles && losesEmbeddedLorebook -> Res.string.already_text_json_lorebook
  fileHoldsNoFiles -> Res.string.already_text_json
  losesEmbeddedLorebook -> Res.string.already_text_lorebook
  else -> Res.string.already_text
}

@Composable
private fun RestOfImportRow(checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
      .padding(bottom = GlorianaTheme.spacing.s16),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlorianaCheckbox(checked, onCheckedChange = null)
    Text(
      stringResource(Res.string.already_rest),
      style = GlorianaTheme.type.material.bodyLarge,
      color = GlorianaTheme.colors.text,
    )
  }
}

@Composable
fun SheetButtons(
  stacked: List<@Composable (Modifier) -> Unit>,
  inRow: List<@Composable (Modifier) -> Unit>,
  modifier: Modifier = Modifier,
) {
  val spacing = GlorianaTheme.spacing
  when (GlorianaTheme.widthClass.sheetStyle) {
    SheetStyle.BottomSheet -> Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
      stacked.forEach { it(Modifier.fillMaxWidth()) }
    }
    SheetStyle.Dialog -> FlowRow(
      modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing.s8, Alignment.End),
      verticalArrangement = Arrangement.spacedBy(spacing.s8),
    ) {
      inRow.forEach { it(Modifier) }
    }
  }
}

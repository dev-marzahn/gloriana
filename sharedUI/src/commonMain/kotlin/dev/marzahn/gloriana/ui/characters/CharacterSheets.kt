package dev.marzahn.gloriana.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import dev.marzahn.gloriana.logic.characters.AssetSource
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.LeftOutOfJson
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaRadioButton
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.library.SheetButtons
import dev.marzahn.gloriana.ui.library.SheetTitle
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.delete
import dev.marzahn.gloriana.ui.resources.delete_part_assets
import dev.marzahn.gloriana.ui.resources.delete_part_avatar
import dev.marzahn.gloriana.ui.resources.delete_part_lorebook
import dev.marzahn.gloriana.ui.resources.delete_text
import dev.marzahn.gloriana.ui.resources.delete_text_with
import dev.marzahn.gloriana.ui.resources.delete_title
import dev.marzahn.gloriana.ui.resources.deleting
import dev.marzahn.gloriana.ui.resources.export_avatar
import dev.marzahn.gloriana.ui.resources.export_avatar_kind
import dev.marzahn.gloriana.ui.resources.export_choose_where
import dev.marzahn.gloriana.ui.resources.export_json
import dev.marzahn.gloriana.ui.resources.export_json_text
import dev.marzahn.gloriana.ui.resources.export_left_out
import dev.marzahn.gloriana.ui.resources.export_left_out_item
import dev.marzahn.gloriana.ui.resources.export_png
import dev.marzahn.gloriana.ui.resources.export_png_no_avatar
import dev.marzahn.gloriana.ui.resources.export_png_text
import dev.marzahn.gloriana.ui.resources.export_text
import dev.marzahn.gloriana.ui.resources.export_title
import dev.marzahn.gloriana.ui.resources.ic_arrow_forward_20
import dev.marzahn.gloriana.ui.resources.list_more
import dev.marzahn.gloriana.ui.resources.list_two
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExportSheet(
  character: Character,
  onChoose: (CardFormat) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var format by rememberSaveable { mutableStateOf(CardFormat.Png) }
  val cancelFocus = remember { FocusRequester() }
  GlorianaSheet(onDismiss, modifier) {
    SheetTitle(stringResource(Res.string.export_title, character.fields.name))
    SheetText(stringResource(Res.string.export_text))
    Column(Modifier.selectableGroup().padding(bottom = GlorianaTheme.spacing.s16)) {
      val pngText = if (character.avatar == null) Res.string.export_png_no_avatar else Res.string.export_png_text
      FormatRow(stringResource(Res.string.export_png), stringResource(pngText), format == CardFormat.Png) {
        format = CardFormat.Png
      }
      val jsonText = stringResource(Res.string.export_json_text)
      FormatRow(stringResource(Res.string.export_json), jsonText, format == CardFormat.Json) {
        format = CardFormat.Json
      }
      val leftOut = character.leftOutOfJson
      if (format == CardFormat.Json && leftOut.isNotEmpty()) LeftOutBox(leftOut)
    }
    val choose = @Composable { buttonModifier: Modifier ->
      GlorianaButton(
        stringResource(Res.string.export_choose_where),
        { onChoose(format) },
        buttonModifier,
        kind = ButtonKind.Primary,
        icon = Res.drawable.ic_arrow_forward_20,
      )
    }
    val cancel = @Composable { buttonModifier: Modifier ->
      val label = stringResource(Res.string.cancel)
      GlorianaButton(label, onDismiss, buttonModifier.focusRequester(cancelFocus), kind = ButtonKind.Ghost)
    }
    SheetButtons(stacked = listOf(choose), inRow = listOf(cancel, choose))
    if (GlorianaTheme.widthClass.sheetStyle == SheetStyle.Dialog) FocusFirstForKeyboard(cancelFocus)
  }
}

@Composable
private fun SheetText(text: String) {
  Text(
    text,
    Modifier.padding(bottom = GlorianaTheme.spacing.s16),
    style = GlorianaTheme.type.material.bodyMedium,
    color = GlorianaTheme.colors.textMuted,
  )
}

@Composable
private fun FormatRow(title: String, line: String, selected: Boolean, onSelect: () -> Unit) {
  val colors = GlorianaTheme.colors
  Row(
    Modifier
      .fillMaxWidth()
      .selectable(selected, role = Role.RadioButton, onClick = onSelect)
      .padding(vertical = GlorianaTheme.spacing.s8),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8),
  ) {
    GlorianaRadioButton(selected, onClick = null)
    Column(Modifier.weight(1f)) {
      Text(title, style = GlorianaTheme.type.material.titleMedium, color = colors.text)
      Text(line, style = GlorianaTheme.type.material.bodyMedium, color = colors.textMuted)
    }
  }
}

@Composable
private fun LeftOutBox(leftOut: List<LeftOutOfJson>) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.surface2, GlorianaTheme.shapes.control)
      .padding(GlorianaTheme.spacing.s12),
    verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6),
  ) {
    Text(stringResource(Res.string.export_left_out), style = type.bodyMedium, color = colors.text)
    val avatar = stringResource(Res.string.export_avatar)
    val avatarKind = stringResource(Res.string.export_avatar_kind)
    leftOut.forEach { item ->
      val (name, kind) = when (item) {
        LeftOutOfJson.Avatar -> avatar to avatarKind
        is LeftOutOfJson.Asset -> item.asset.name to assetKind(item.asset.type)
      }
      val line = stringResource(Res.string.export_left_out_item, name, kind)
      Text(line, style = type.bodyMedium, color = colors.textMuted)
    }
  }
}

@Composable
fun DeleteSheet(
  character: Character,
  deleting: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cancelFocus = remember { FocusRequester() }
  val name = character.fields.name
  GlorianaSheet({ if (!deleting) onDismiss() }, modifier) {
    SheetTitle(stringResource(Res.string.delete_title, name))
    SheetText(deleteMessage(character))
    val delete = @Composable { buttonModifier: Modifier ->
      val label = if (deleting) Res.string.deleting else Res.string.delete
      GlorianaButton(stringResource(label), onConfirm, buttonModifier, kind = ButtonKind.Danger, enabled = !deleting)
    }
    val cancel = @Composable { buttonModifier: Modifier ->
      GlorianaButton(
        stringResource(Res.string.cancel),
        onDismiss,
        buttonModifier.focusRequester(cancelFocus),
        kind = ButtonKind.Ghost,
        enabled = !deleting,
      )
    }
    SheetButtons(stacked = listOf(delete, cancel), inRow = listOf(cancel, delete))
    FocusFirstForKeyboard(cancelFocus)
  }
}

@Composable
private fun deleteMessage(character: Character): String {
  val otherAssets = character.assets.count { it.source is AssetSource.Stored }
  val parts = listOfNotNull(
    character.avatar?.let { stringResource(Res.string.delete_part_avatar) },
    otherAssets.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.delete_part_assets, it, it) },
    stringResource(Res.string.delete_part_lorebook).takeIf { character.keptFields.hasEmbeddedLorebook },
  )
  val name = character.fields.name
  if (parts.isEmpty()) return stringResource(Res.string.delete_text, name)
  val lastPattern = stringResource(Res.string.list_two, LIST_SLOT_A, LIST_SLOT_B)
  val pattern = stringResource(Res.string.list_more, LIST_SLOT_A, LIST_SLOT_B)
  return stringResource(Res.string.delete_text_with, name, joinedList(parts, lastPattern, pattern))
}

private const val LIST_SLOT_A = ""
private const val LIST_SLOT_B = ""

fun joinedList(parts: List<String>, lastPattern: String, pattern: String): String =
  parts.reduceIndexed { index, joined, part ->
    val template = if (index == parts.lastIndex) lastPattern else pattern
    template.replace(LIST_SLOT_A, joined).replace(LIST_SLOT_B, part)
  }

package dev.marzahn.gloriana.ui.presets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.GlorianaMoreMenu
import dev.marzahn.gloriana.ui.components.GlorianaSwitch
import dev.marzahn.gloriana.ui.components.MenuItem
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.delete_group
import dev.marzahn.gloriana.ui.resources.delete_group_detail
import dev.marzahn.gloriana.ui.resources.filled_in
import dev.marzahn.gloriana.ui.resources.fold_group
import dev.marzahn.gloriana.ui.resources.group_empty_hint
import dev.marzahn.gloriana.ui.resources.group_no_entries
import dev.marzahn.gloriana.ui.resources.ic_auto_awesome
import dev.marzahn.gloriana.ui.resources.ic_chevron_right
import dev.marzahn.gloriana.ui.resources.ic_delete
import dev.marzahn.gloriana.ui.resources.ic_drag_indicator
import dev.marzahn.gloriana.ui.resources.ic_edit
import dev.marzahn.gloriana.ui.resources.ic_expand_more
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.rename_group
import dev.marzahn.gloriana.ui.resources.unfold_group
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val HandleWidth = 32.dp
private val HandleHeight = 48.dp
private val HandleIcon = 20.dp
private val FilledIcon = 16.dp
private val GroupIndent = 16.dp
private val GroupRowHeight = 52.dp

private val entryRowHeight: Dp
  @Composable get() = if (GlorianaTheme.widthClass.pointerDense) 48.dp else 60.dp

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
  Box(modifier.size(HandleWidth, HandleHeight), contentAlignment = Alignment.Center) {
    Icon(
      painterResource(Res.drawable.ic_drag_indicator),
      null,
      Modifier.size(HandleIcon),
      tint = GlorianaTheme.colors.textSubtle,
    )
  }
}

@Composable
internal fun EntryRow(
  entry: PresetEntry,
  indented: Boolean,
  actions: EntriesActions,
  handleModifier: Modifier = Modifier,
  movesModifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  val tone = if (entry.enabled) colors.text else colors.textSubtle
  val lineTone = if (entry.enabled) colors.textMuted else colors.textSubtle
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = entryRowHeight)
      .tappable(GlorianaTheme.shapes.control) { actions.onOpenEntry(entry.id) }
      .then(movesModifier)
      .padding(start = spacing.s8 + if (indented) GroupIndent else 0.dp, end = spacing.s16),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s8),
  ) {
    DragHandle(handleModifier)
    Column(Modifier.weight(1f)) {
      Text(entry.name, style = type.titleMedium, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (entry.filledByApp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s6)) {
          Icon(painterResource(Res.drawable.ic_auto_awesome), null, Modifier.size(FilledIcon), tint = lineTone)
          Text(
            stringResource(Res.string.filled_in, entry.filledWithText()),
            style = type.bodyMedium,
            color = lineTone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else {
        Text(
          entry.placementLine(),
          style = type.bodyMedium,
          color = lineTone,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    GlorianaSwitch(entry.enabled, { actions.onIntent(PresetIntent.SetOn(entry.id, it)) })
  }
}

@Composable
internal fun GroupRow(
  group: PresetGroup,
  entries: List<PresetEntry>,
  folded: Boolean,
  onFold: () -> Unit,
  actions: EntriesActions,
  handleModifier: Modifier = Modifier,
  movesModifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  Column(Modifier.fillMaxWidth().background(colors.surface2)) {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = GroupRowHeight)
        .semantics(mergeDescendants = true) {}
        .then(movesModifier)
        .padding(start = spacing.s8, end = spacing.s8),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DragHandle(handleModifier)
      val standard = GlorianaTheme.motion.standard
      AnimatedContent(
        folded,
        transitionSpec = { fadeIn(standard.tween()) togetherWith fadeOut(standard.tween()) },
      ) { shownFolded ->
        GlorianaIconButton(
          if (shownFolded) Res.drawable.ic_chevron_right else Res.drawable.ic_expand_more,
          stringResource(if (shownFolded) Res.string.unfold_group else Res.string.fold_group, group.name),
          onFold,
        )
      }
      Column(Modifier.weight(1f).padding(start = spacing.s6)) {
        Text(group.name, style = type.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val count = if (entries.isEmpty()) {
          stringResource(Res.string.group_no_entries)
        } else {
          entriesLine(entries.size, entries.count { it.enabled })
        }
        Text(count, style = type.bodySmall, color = colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      GlorianaMoreMenu(
        stringResource(Res.string.more_for, group.name),
        listOf(
          MenuItem(stringResource(Res.string.rename_group), Res.drawable.ic_edit, {
            actions.onAskGroup(GroupPrompt.Rename(group.id, group.name))
          }),
          MenuItem(
            stringResource(Res.string.delete_group),
            Res.drawable.ic_delete,
            { actions.onIntent(PresetIntent.DeleteGroup(group.id)) },
            detail = stringResource(Res.string.delete_group_detail),
          ),
        ),
      )
    }
    if (entries.isEmpty()) {
      Text(
        stringResource(Res.string.group_empty_hint),
        Modifier.padding(start = spacing.s16 + HandleWidth, end = spacing.s16, bottom = spacing.s12),
        style = type.bodyMedium,
        color = colors.textSubtle,
      )
    }
  }
}

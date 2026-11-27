package dev.marzahn.gloriana.ui.presets

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaRadioButton
import dev.marzahn.gloriana.ui.components.GlorianaSwitch
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.Segment
import dev.marzahn.gloriana.ui.components.SegmentWidth
import dev.marzahn.gloriana.ui.components.SegmentedControl
import dev.marzahn.gloriana.ui.components.SettingRow
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.depth
import dev.marzahn.gloriana.ui.resources.depth_error
import dev.marzahn.gloriana.ui.resources.depth_hint
import dev.marzahn.gloriana.ui.resources.entry_filled_hint
import dev.marzahn.gloriana.ui.resources.entry_filled_text
import dev.marzahn.gloriana.ui.resources.entry_how_sent
import dev.marzahn.gloriana.ui.resources.entry_in_chat_hint
import dev.marzahn.gloriana.ui.resources.entry_in_chat_title
import dev.marzahn.gloriana.ui.resources.entry_in_order_hint
import dev.marzahn.gloriana.ui.resources.entry_in_order_title
import dev.marzahn.gloriana.ui.resources.entry_macros_hint
import dev.marzahn.gloriana.ui.resources.entry_of
import dev.marzahn.gloriana.ui.resources.entry_on
import dev.marzahn.gloriana.ui.resources.entry_on_hint
import dev.marzahn.gloriana.ui.resources.entry_role
import dev.marzahn.gloriana.ui.resources.entry_text
import dev.marzahn.gloriana.ui.resources.entry_where
import dev.marzahn.gloriana.ui.resources.ic_arrow_downward
import dev.marzahn.gloriana.ui.resources.ic_arrow_upward
import dev.marzahn.gloriana.ui.resources.ic_auto_awesome
import dev.marzahn.gloriana.ui.resources.ic_notes
import dev.marzahn.gloriana.ui.resources.ic_send_20
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.move_up
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DepthFieldWidth = 120.dp
private val RadioIndent = 56.dp
private val FilledIconSize = 20.dp
private val SkeletonLine = 180.dp

internal fun depthOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

private class DepthEntry(text: String, showError: Boolean = false) {
  var text by mutableStateOf(text)
    private set
  var showError by mutableStateOf(showError)
  val focus = FocusRequester()

  fun type(value: String) {
    text = value
    showError = false
  }

  companion object {
    val Saver = listSaver<DepthEntry, Any>(
      save = { listOf(it.text, it.showError) },
      restore = { DepthEntry(it[0] as String, it[1] as Boolean) },
    )
  }
}

@Composable
fun PresetEntryScreen(route: PresetEntryRoute, modifier: Modifier = Modifier) {
  val viewModel = rememberPresetPage(route)
  val navigator = LocalShellNavigator.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val page = state as? PresetPageState.Shown
  val entry = page?.entry(route.entryUuid)
  LaunchedEffect(page != null, entry == null) {
    if (page != null && entry == null) navigator.close(Destination.Library, route)
  }
  val storedEntry = page?.stored?.content?.entries?.firstOrNull { it.id == route.entryUuid }
  val shownDepth = (entry?.placement as? EntryPlacement.InChat)?.depth
    ?: (storedEntry?.placement as? EntryPlacement.InChat)?.depth
    ?: 0
  val loaded = entry != null
  val depth = rememberSaveable(route, loaded, saver = DepthEntry.Saver) { DepthEntry(shownDepth.toString()) }
  val inChat = entry?.placement is EntryPlacement.InChat
  val depthBad = inChat && depthOf(depth.text) == null
  val checkDepth = {
    if (depthBad) {
      depth.showError = true
      depth.focus.requestFocus()
    }
    !depthBad
  }
  val leave = { if (checkDepth()) navigator.pop(Destination.Library) }
  NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = depthBad,
    onBackCompleted = { checkDepth() },
  )
  PresetPageFrame(leave, page?.name.orEmpty(), modifier, backOnEveryWidth = true) {
    if (page == null || entry == null) {
      Skeleton { SkeletonBlock(Modifier.size(width = SkeletonLine, height = GlorianaTheme.spacing.s20)) }
    } else {
      EntryPage(
        page,
        entry,
        depth,
        onDepth = { text ->
          depth.type(text)
          depthOf(text)?.let { viewModel.on(PresetIntent.SetPlacement(entry.id, EntryPlacement.InChat(it))) }
        },
        inChatDepth = { depthOf(depth.text) ?: shownDepth },
        checkDepth = checkDepth,
        onIntent = viewModel::on,
      )
    }
  }
}

@Composable
private fun EntryPage(
  page: PresetPageState.Shown,
  entry: PresetEntry,
  depth: DepthEntry,
  onDepth: (String) -> Unit,
  inChatDepth: () -> Int,
  checkDepth: () -> Boolean,
  onIntent: (PresetIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  val place = page.content.rows.placeOf(entry.id)
  Column(modifier) {
    Column(Modifier.padding(top = spacing.s8), verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
      Text(entry.name, style = type.headlineLarge, color = colors.text)
      if (place != null) {
        Text(
          stringResource(Res.string.entry_of, place.position, place.of, page.name),
          style = type.bodyMedium,
          color = colors.textMuted,
        )
      }
    }
    EntryActions(page, entry, checkDepth, onIntent)
    SaveNote(page)
    HowItsSent(entry, depth, onDepth, inChatDepth, onIntent)
    TextPanel(entry, Modifier.padding(top = spacing.s24))
  }
}

@Composable
private fun EntryActions(
  page: PresetPageState.Shown,
  entry: PresetEntry,
  checkDepth: () -> Boolean,
  onIntent: (PresetIntent) -> Unit,
) {
  val rows = page.content.rows
  val spacing = GlorianaTheme.spacing
  Row(
    Modifier.padding(top = spacing.s16, bottom = spacing.s8),
    horizontalArrangement = Arrangement.spacedBy(spacing.s8),
  ) {
    SaveButton(page, onIntent, beforeSave = checkDepth)
    GlorianaButton(
      stringResource(Res.string.move_up),
      { onIntent(PresetIntent.Move(entry.id, MoveDirection.Up)) },
      icon = Res.drawable.ic_arrow_upward,
      enabled = rows.canMove(entry.id, MoveDirection.Up),
    )
    GlorianaButton(
      stringResource(Res.string.move_down),
      { onIntent(PresetIntent.Move(entry.id, MoveDirection.Down)) },
      icon = Res.drawable.ic_arrow_downward,
      enabled = rows.canMove(entry.id, MoveDirection.Down),
    )
  }
}

@Composable
private fun HowItsSent(
  entry: PresetEntry,
  depth: DepthEntry,
  onDepth: (String) -> Unit,
  inChatDepth: () -> Int,
  onIntent: (PresetIntent) -> Unit,
) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val placeable = !entry.filledByApp
  val roles = EntryRole.entries.map { Segment(it, stringResource(it.label)) }
  Panel(
    Res.drawable.ic_send_20,
    stringResource(Res.string.entry_how_sent),
    listOf(
      {
        SettingRow(stringResource(Res.string.entry_on), stringResource(Res.string.entry_on_hint), null) {
          GlorianaSwitch(entry.enabled, { onIntent(PresetIntent.SetOn(entry.id, it)) })
        }
      },
      {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
          Text(stringResource(Res.string.entry_role), style = type.bodyLarge, color = colors.text)
          SegmentedControl(
            roles,
            entry.role,
            { onIntent(PresetIntent.SetRole(entry.id, it)) },
            width = SegmentWidth.Filling,
            enabled = placeable,
          )
          if (!placeable) FieldNote(stringResource(Res.string.entry_filled_hint), null)
        }
      },
      {
        Column(Modifier.animateContentSize(GlorianaTheme.motion.standard.tween())) {
          Text(stringResource(Res.string.entry_where), style = type.bodyLarge, color = colors.text)
          PlacementChoice(
            stringResource(Res.string.entry_in_order_title),
            stringResource(Res.string.entry_in_order_hint),
            selected = entry.placement == EntryPlacement.Relative,
            enabled = placeable,
          ) { onIntent(PresetIntent.SetPlacement(entry.id, EntryPlacement.Relative)) }
          PlacementChoice(
            stringResource(Res.string.entry_in_chat_title),
            stringResource(Res.string.entry_in_chat_hint),
            selected = entry.placement is EntryPlacement.InChat,
            enabled = placeable,
          ) { onIntent(PresetIntent.SetPlacement(entry.id, EntryPlacement.InChat(inChatDepth()))) }
          if (entry.placement is EntryPlacement.InChat) DepthField(depth, onDepth, placeable)
        }
      },
    ),
  )
}

@Composable
private fun PlacementChoice(title: String, hint: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
  val spacing = GlorianaTheme.spacing
  Row(
    Modifier.fillMaxWidth().tappable(GlorianaTheme.shapes.control, enabled = enabled, onClick = onSelect),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s8),
  ) {
    GlorianaRadioButton(selected, null, enabled = enabled)
    Column {
      Text(title, style = GlorianaTheme.type.material.bodyLarge, color = GlorianaTheme.colors.text)
      FieldNote(hint, null)
    }
  }
}

@Composable
private fun DepthField(depth: DepthEntry, onDepth: (String) -> Unit, enabled: Boolean) {
  val spacing = GlorianaTheme.spacing
  Column(
    Modifier.padding(start = RadioIndent, top = spacing.s8),
    verticalArrangement = Arrangement.spacedBy(spacing.s6),
  ) {
    Text(
      stringResource(Res.string.depth),
      style = GlorianaTheme.type.material.labelMedium,
      color = GlorianaTheme.colors.textSubtle,
    )
    GlorianaTextField(
      depth.text,
      onDepth,
      Modifier.width(DepthFieldWidth).focusRequester(depth.focus),
      enabled = enabled,
      error = if (depth.showError) stringResource(Res.string.depth_error) else null,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    FieldNote(
      hint = stringResource(Res.string.depth_hint),
      error = if (depth.showError) stringResource(Res.string.depth_error) else null,
    )
  }
}

@Composable
private fun TextPanel(entry: PresetEntry, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type
  val spacing = GlorianaTheme.spacing
  Panel(
    Res.drawable.ic_notes,
    stringResource(Res.string.entry_text),
    listOf(
      {
        if (entry.filledByApp) {
          Row(horizontalArrangement = Arrangement.spacedBy(spacing.s10)) {
            Icon(
              painterResource(Res.drawable.ic_auto_awesome),
              null,
              Modifier.size(FilledIconSize),
              tint = colors.textMuted,
            )
            Text(
              stringResource(Res.string.entry_filled_text, entry.filledWithText()),
              style = type.material.bodyMedium,
              color = colors.textMuted,
            )
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
            Text(entry.text, style = type.reading, color = colors.text)
            FieldNote(stringResource(Res.string.entry_macros_hint), null)
          }
        }
      },
    ),
    modifier,
  )
}

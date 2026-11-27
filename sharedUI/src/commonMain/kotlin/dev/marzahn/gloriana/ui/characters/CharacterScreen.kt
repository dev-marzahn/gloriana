package dev.marzahn.gloriana.ui.characters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.ExportFailure
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.components.SkeletonSurface
import dev.marzahn.gloriana.ui.library.StartChatButton
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.avatar_full_screen
import dev.marzahn.gloriana.ui.resources.all_characters
import dev.marzahn.gloriana.ui.resources.delete_character
import dev.marzahn.gloriana.ui.resources.delete_failed
import dev.marzahn.gloriana.ui.resources.delete_failed_detail
import dev.marzahn.gloriana.ui.resources.edit
import dev.marzahn.gloriana.ui.resources.export
import dev.marzahn.gloriana.ui.resources.export_again
import dev.marzahn.gloriana.ui.resources.export_failed
import dev.marzahn.gloriana.ui.resources.export_failed_cant_write
import dev.marzahn.gloriana.ui.resources.export_failed_storage
import dev.marzahn.gloriana.ui.resources.export_failed_unreadable
import dev.marzahn.gloriana.ui.resources.export_incomplete
import dev.marzahn.gloriana.ui.resources.export_incomplete_access
import dev.marzahn.gloriana.ui.resources.export_incomplete_storage
import dev.marzahn.gloriana.ui.resources.exporting
import dev.marzahn.gloriana.ui.resources.ic_arrow_back
import dev.marzahn.gloriana.ui.resources.ic_delete
import dev.marzahn.gloriana.ui.resources.ic_edit
import dev.marzahn.gloriana.ui.resources.ic_file_save_20
import dev.marzahn.gloriana.ui.resources.ic_more_horiz
import dev.marzahn.gloriana.ui.resources.more
import dev.marzahn.gloriana.ui.resources.not_available_yet
import dev.marzahn.gloriana.ui.resources.page_error
import dev.marzahn.gloriana.ui.resources.page_error_detail
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

private val HeaderAvatar = 56.dp
private val BackIconSize = 16.dp
private val BackButtonHeight = 40.dp
private val SkeletonName = 160.dp
private val SkeletonMeta = 100.dp
private val SkeletonActions = 40.dp
private val SkeletonPanel = 200.dp

class PageActions(
  val onBack: () -> Unit = {},
  val onRetry: () -> Unit = {},
  val onExport: () -> Unit = {},
  val onDismissExportNote: () -> Unit = {},
  val onAvatar: () -> Unit = {},
  val onDelete: () -> Unit = {},
)

@Composable
fun CharacterScreen(id: Uuid, modifier: Modifier = Modifier, viewModel: CharacterViewModel = koinViewModel()) {
  val navigator = LocalShellNavigator.current
  DisposableEffect(viewModel, id) {
    viewModel.show(id)
    val release = viewModel.pageShown()
    onDispose { release() }
  }
  LaunchedEffect(viewModel) { viewModel.closeRequests.collect { navigator.pop(Destination.Library) } }
  val state by viewModel.state.collectAsStateWithLifecycle()
  var exporting by rememberSaveable { mutableStateOf(false) }
  var enlarged by rememberSaveable { mutableStateOf(false) }
  var pendingFormat by rememberSaveable { mutableStateOf<CardFormat?>(null) }
  val saver = rememberCardSaver { target -> pendingFormat?.let { viewModel.export(it, target) } }
  val actions = PageActions(
    onBack = { navigator.pop(Destination.Library) },
    onRetry = viewModel::retry,
    onExport = { exporting = true },
    onDismissExportNote = viewModel::dismissExportNote,
    onAvatar = { enlarged = true },
    onDelete = viewModel::askToDelete,
  )
  CharacterContent(state, actions, modifier)
  val shown = state as? CharacterPageState.Shown ?: return
  val character = shown.character
  if (exporting) {
    ExportSheet(character, onChoose = { format ->
      exporting = false
      pendingFormat = format
      saver.choose(proposedFileName(character.fields.name, format), format)
    }, onDismiss = { exporting = false })
  }
  if (shown.delete == DeleteProgress.Confirming || shown.delete == DeleteProgress.Deleting) {
    DeleteSheet(character, shown.delete == DeleteProgress.Deleting, viewModel::confirmDelete, viewModel::cancelDelete)
  }
  val path = shown.avatarPath
  if (enlarged && path != null) FullScreenAvatar(character.fields.name, path) { enlarged = false }
}

@Composable
internal fun CharacterContent(state: CharacterPageState, actions: PageActions, modifier: Modifier = Modifier) {
  val spacing = GlorianaTheme.spacing
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.s16)) {
    if (GlorianaTheme.widthClass.navigation != Navigation.Workbench) BackToCharacters(actions.onBack)
    when (state) {
      CharacterPageState.Loading -> Skeleton { PageSkeleton() }
      CharacterPageState.CouldNotLoad -> NoteCard {
        NoteLine(boldThenPlain(stringResource(Res.string.page_error), stringResource(Res.string.page_error_detail)))
        GlorianaButton(stringResource(Res.string.retry), actions.onRetry)
      }
      CharacterPageState.Gone -> Unit
      is CharacterPageState.Shown -> ShownPage(state, actions)
    }
  }
}

@Composable
private fun BackToCharacters(onBack: () -> Unit) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Row(
    Modifier
      .heightIn(min = BackButtonHeight)
      .tappable(GlorianaTheme.shapes.control, onClick = onBack)
      .padding(horizontal = spacing.s8),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s6),
  ) {
    Icon(painterResource(Res.drawable.ic_arrow_back), null, Modifier.size(BackIconSize), tint = colors.textMuted)
    val label = stringResource(Res.string.all_characters)
    Text(label, style = GlorianaTheme.type.material.labelLarge, color = colors.textMuted)
  }
}

@Composable
private fun ShownPage(state: CharacterPageState.Shown, actions: PageActions) {
  val spacing = GlorianaTheme.spacing
  val character = state.character
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s16)) {
    Header(character, state.avatarPath, state.export is ExportState.Running, actions)
    exportNote(state.export)?.let { note ->
      NoteCard(onDismiss = actions.onDismissExportNote) {
        NoteLine(note)
        GlorianaButton(stringResource(Res.string.export_again), actions.onExport, icon = Res.drawable.ic_file_save_20)
      }
    }
    if (state.delete == DeleteProgress.Failed) {
      NoteCard {
        val headline = stringResource(Res.string.delete_failed, character.fields.name)
        NoteLine(boldThenPlain(headline, stringResource(Res.string.delete_failed_detail)))
        GlorianaButton(stringResource(Res.string.retry), actions.onDelete)
      }
    }
    ActionsRow(state.export, actions)
    CharacterPanels(character)
  }
}

@Composable
private fun Header(character: Character, avatarPath: String?, exporting: Boolean, actions: PageActions) {
  val spacing = GlorianaTheme.spacing
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.panel
  var menu by rememberSaveable { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s12)) {
    val avatarLabel = stringResource(Res.string.avatar_full_screen, character.fields.name)
    val opens = if (avatarPath != null) {
      Modifier.tappable(shape, Role.Image, avatarLabel, onClick = actions.onAvatar)
    } else {
      Modifier
    }
    val avatarModifier = Modifier.size(HeaderAvatar).clip(shape).then(opens)
    CharacterPicture(character.id, character.fields.name, avatarPath, avatarModifier)
    Column(Modifier.weight(1f)) {
      Text(character.fields.name, style = GlorianaTheme.type.material.headlineLarge, color = colors.text)
      metaLine(character.fields.creator, character.fields.characterVersion)?.let {
        Text(it, style = GlorianaTheme.type.material.bodyMedium, color = colors.textMuted)
      }
    }
    val notYet = stringResource(Res.string.not_available_yet)
    val edit = stringResource(Res.string.edit)
    GlorianaIconButton(Res.drawable.ic_edit, edit, {}, enabled = false, stateDescription = notYet)
    Box {
      GlorianaIconButton(Res.drawable.ic_more_horiz, stringResource(Res.string.more), { menu = true })
      DropdownMenu(menu, { menu = false }, containerColor = colors.raised) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.delete_character)) },
          onClick = {
            menu = false
            actions.onDelete()
          },
          leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
          enabled = !exporting,
        )
      }
    }
  }
}

@Composable
private fun ActionsRow(export: ExportState?, actions: PageActions) {
  val spacing = GlorianaTheme.spacing
  val expanded = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val running = export as? ExportState.Running
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s8)) {
      if (!expanded) StartChatButton()
      GlorianaButton(
        stringResource(Res.string.export),
        actions.onExport,
        kind = if (expanded) ButtonKind.Primary else ButtonKind.Secondary,
        icon = Res.drawable.ic_file_save_20,
        enabled = running == null,
      )
    }
    running?.let { RunningLine(stringResource(Res.string.exporting, it.fileName)) }
  }
}

@Composable
private fun exportNote(export: ExportState?): AnnotatedString? {
  val failed = export as? ExportState.NotSaved ?: return null
  val problem = failed.problem
  return if (problem.incompleteFileLeft) {
    val detail = when (problem.reason) {
      ExportFailure.StorageFull -> Res.string.export_incomplete_storage
      else -> Res.string.export_incomplete_access
    }
    boldThenPlain(stringResource(Res.string.export_incomplete, failed.fileName), stringResource(detail))
  } else {
    val detail = when (problem.reason) {
      ExportFailure.StorageFull -> Res.string.export_failed_storage
      ExportFailure.CantWrite -> Res.string.export_failed_cant_write
      ExportFailure.StoredFileUnreadable -> Res.string.export_failed_unreadable
    }
    boldThenPlain(stringResource(Res.string.export_failed, failed.fileName), stringResource(detail))
  }
}

@Composable
private fun PageSkeleton() {
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s16)) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s12)) {
      SkeletonBlock(Modifier.size(HeaderAvatar))
      Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
        SkeletonBlock(Modifier.size(width = SkeletonName, height = spacing.s20))
        SkeletonBlock(Modifier.size(width = SkeletonMeta, height = spacing.s12))
      }
    }
    SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonActions))
    SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanel), on = SkeletonSurface.Raised)
  }
}

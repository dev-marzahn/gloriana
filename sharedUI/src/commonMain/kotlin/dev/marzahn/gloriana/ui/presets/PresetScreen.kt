package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.DetailHeader
import dev.marzahn.gloriana.ui.components.DetailPage
import dev.marzahn.gloriana.ui.components.DetailSkeleton
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaMoreMenu
import dev.marzahn.gloriana.ui.components.MenuItem
import dev.marzahn.gloriana.ui.components.NotDeletedNote
import dev.marzahn.gloriana.ui.components.NotSavedNote
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.PageBack
import dev.marzahn.gloriana.ui.components.PageName
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.RunningLine
import dev.marzahn.gloriana.ui.components.SaveButton
import dev.marzahn.gloriana.ui.components.NotSavedCopy
import dev.marzahn.gloriana.ui.components.SkeletonHeader
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.files.ExportState
import dev.marzahn.gloriana.ui.files.SavedFileType
import dev.marzahn.gloriana.ui.files.exportNote
import dev.marzahn.gloriana.ui.files.proposedFileName
import dev.marzahn.gloriana.ui.files.rememberFileSaver
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.GuardUnsavedChanges
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.UnsavedPage
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.back_to_presets
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.delete_preset
import dev.marzahn.gloriana.ui.resources.export
import dev.marzahn.gloriana.ui.resources.export_again
import dev.marzahn.gloriana.ui.resources.exporting
import dev.marzahn.gloriana.ui.resources.ic_delete
import dev.marzahn.gloriana.ui.resources.ic_file_save_20
import dev.marzahn.gloriana.ui.resources.ic_star
import dev.marzahn.gloriana.ui.resources.ic_view_list
import dev.marzahn.gloriana.ui.resources.make_default
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.not_available_yet
import dev.marzahn.gloriana.ui.resources.preset_error
import dev.marzahn.gloriana.ui.resources.preset_error_detail
import dev.marzahn.gloriana.ui.resources.preset_not_saved_detail
import dev.marzahn.gloriana.ui.resources.preset_not_saved_storage_detail
import dev.marzahn.gloriana.ui.resources.save_changes_first
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun rememberPresetPage(part: PresetPart): PresetViewModel {
  val viewModel: PresetViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val sessions: PresetSessions = koinInject()
  LaunchedEffect(viewModel, part.presetUuid) { viewModel.show(part.presetUuid) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state == PresetPageState.Gone) navigator.closeWithoutAsking(Destination.Library, part) }
  if (part is PresetRoute) {
    val name = (state as? PresetPageState.Shown)?.name
    val leavingMessage = name?.let { stringResource(Res.string.changes_not_saved, it) }
    GuardUnsavedChanges(part) {
      val id = part.presetUuid
      if (leavingMessage != null && (viewModel.state.value as? PresetPageState.Shown)?.hasChanges == true) {
        UnsavedPage(leavingMessage, quittingName = name, discard = { sessions.discard(id) })
      } else {
        null
      }
    }
    DisposableEffect(part) {
      onDispose {
        if (part !in navigator.stack(Destination.Library)) sessions.importNotes(part.presetUuid, null)
      }
    }
  }
  return viewModel
}

@Composable
fun PresetScreen(route: PresetRoute, modifier: Modifier = Modifier) {
  val viewModel = rememberPresetPage(route)
  val navigator = LocalShellNavigator.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  var asking by rememberGroupPrompt()
  val exports: PresetExports = koinInject()
  DisposableEffect(exports, route) {
    exports.runs.pageShown(route.presetUuid)
    onDispose { exports.runs.pageHidden(route.presetUuid) }
  }
  val saver = rememberFileSaver { target -> viewModel.on(PresetIntent.Export(target)) }
  val shown = state as? PresetPageState.Shown
  val actions = PresetPageActions(
    onBack = { navigator.pop(Destination.Library) },
    onIntent = viewModel::on,
    onOpen = navigator::push,
    onExport = { shown?.let { saver.choose(proposedFileName(it.name, SavedFileType.Json), SavedFileType.Json) } },
  )
  PresetPageContent(state, actions, onAskGroup = { asking = it }, modifier)
  shown?.let { page ->
    PresetDeleteQuestion(page, viewModel::on)
    asking?.let { prompt ->
      GroupPromptSheet(prompt, page.content, onDone = { intent ->
        asking = null
        intent?.let(viewModel::on)
      })
    }
  }
}

@Composable
internal fun PresetPageContent(
  state: PresetPageState,
  actions: PresetPageActions,
  onAskGroup: (GroupPrompt) -> Unit,
  modifier: Modifier = Modifier,
) {
  DetailPage(PageBack(stringResource(Res.string.back_to_presets), actions.onBack), modifier) {
    when (state) {
      PresetPageState.Loading, PresetPageState.Gone -> DetailSkeleton(SkeletonHeader.Tile, buttons = 1)
      PresetPageState.CouldNotLoad ->
        RetryNote(stringResource(Res.string.preset_error), Res.string.preset_error_detail) {
          actions.onIntent(PresetIntent.Retry)
        }
      is PresetPageState.Shown -> ShownPage(state, actions, onAskGroup)
    }
  }
}

@Composable
private fun ShownPage(
  page: PresetPageState.Shown,
  actions: PresetPageActions,
  onAskGroup: (GroupPrompt) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = GlorianaTheme.spacing
  val id = page.stored.id
  Column(modifier) {
    page.session.importNotes?.let { notes ->
      ImportNotesView(notes, page.name, { actions.onIntent(PresetIntent.DismissImportNotes) })
    }
    Header(page, actions)
    Box(Modifier.padding(bottom = spacing.s8)) { PresetSaveButton(page, actions.onIntent) }
    Outcome(page, actions)
    EntriesPanel(
      page.content.rows,
      EntriesActions(
        onIntent = actions.onIntent,
        onOpenEntry = { actions.onOpen(PresetEntryRoute(id, it)) },
        onAskGroup = onAskGroup,
      ),
      Modifier.padding(top = spacing.s8),
    )
    WrappersPanel(page, { actions.onOpen(PresetWrapperRoute(id, it)) }, Modifier.padding(top = spacing.s24))
  }
}

@Composable
private fun Header(page: PresetPageState.Shown, actions: PresetPageActions) {
  val entries = page.content.entries
  DetailHeader(
    Res.drawable.ic_view_list,
    name = PageName.Named(page.name),
    modifier = Modifier.padding(bottom = GlorianaTheme.spacing.s16),
    meta = {
      Text(
        entriesLine(entries.size, entries.count { it.enabled }),
        style = GlorianaTheme.type.material.bodyMedium,
        color = GlorianaTheme.colors.textMuted,
      )
    },
    actions = { PresetMoreMenu(page, actions) },
  )
}

@Composable
private fun PresetMoreMenu(page: PresetPageState.Shown, actions: PresetPageActions) {
  val exporting = page.export is ExportState.Running
  val items = listOf(
    MenuItem(
      stringResource(Res.string.make_default),
      Res.drawable.ic_star,
      onClick = {},
      enabled = false,
      detail = stringResource(Res.string.not_available_yet),
    ),
    MenuItem(
      stringResource(Res.string.export),
      Res.drawable.ic_file_save_20,
      onClick = actions.onExport,
      enabled = !page.hasChanges && !exporting,
      detail = if (page.hasChanges) stringResource(Res.string.save_changes_first) else null,
    ),
    MenuItem(stringResource(Res.string.delete_preset), Res.drawable.ic_delete, {
      actions.onIntent(PresetIntent.AskToDelete)
    }),
  )
  GlorianaMoreMenu(stringResource(Res.string.more_for, page.name), items)
}

@Composable
internal fun PresetSaveButton(
  page: PresetPageState.Shown,
  onIntent: (PresetIntent) -> Unit,
  beforeSave: () -> Boolean = { true },
) {
  SaveButton(page.unsaved.isSaving, page.canSave, { if (beforeSave()) onIntent(PresetIntent.Save) })
}

@Composable
internal fun SaveNote(page: PresetPageState.Shown, modifier: Modifier = Modifier) {
  val failure = page.session.notSaved ?: return
  NotSavedNote(failure, PresetSaveFailures, page.name, modifier.padding(bottom = GlorianaTheme.spacing.s8))
}

private val PresetSaveFailures =
  NotSavedCopy.forANamedItem(Res.string.preset_not_saved_storage_detail, Res.string.preset_not_saved_detail)

@Composable
private fun Outcome(page: PresetPageState.Shown, actions: PresetPageActions) {
  val spacing = GlorianaTheme.spacing
  Column(Modifier.fillMaxWidth()) {
    SaveNote(page)
    if (page.session.notDeleted) {
      NotDeletedNote(
        page.name,
        onRetry = { actions.onIntent(PresetIntent.AskToDelete) },
        Modifier.padding(bottom = spacing.s8),
      )
    }
    when (val export = page.export) {
      is ExportState.Running ->
        RunningLine(stringResource(Res.string.exporting, export.fileName), Modifier.padding(bottom = spacing.s8))
      is ExportState.NotSaved -> exportNote(export)?.let { note ->
        val dismiss = { actions.onIntent(PresetIntent.DismissExportNote) }
        NoteCard(Modifier.padding(bottom = spacing.s8), onDismiss = dismiss) {
          NoteLine(note)
          GlorianaButton(stringResource(Res.string.export_again), actions.onExport, icon = Res.drawable.ic_file_save_20)
        }
      }
      null -> Unit
    }
  }
}

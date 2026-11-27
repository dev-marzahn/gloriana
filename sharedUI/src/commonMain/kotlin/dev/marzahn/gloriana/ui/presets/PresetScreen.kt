package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.BackButton
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaMoreMenu
import dev.marzahn.gloriana.ui.components.MenuItem
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.RunningLine
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.components.SkeletonSurface
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
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_delete
import dev.marzahn.gloriana.ui.resources.ic_file_save_20
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.ic_star
import dev.marzahn.gloriana.ui.resources.ic_view_list
import dev.marzahn.gloriana.ui.resources.make_default
import dev.marzahn.gloriana.ui.resources.not_available_yet
import dev.marzahn.gloriana.ui.resources.preset_error
import dev.marzahn.gloriana.ui.resources.preset_error_detail
import dev.marzahn.gloriana.ui.resources.preset_more
import dev.marzahn.gloriana.ui.resources.preset_not_deleted
import dev.marzahn.gloriana.ui.resources.preset_not_deleted_detail
import dev.marzahn.gloriana.ui.resources.preset_not_saved
import dev.marzahn.gloriana.ui.resources.preset_not_saved_detail
import dev.marzahn.gloriana.ui.resources.preset_not_saved_storage
import dev.marzahn.gloriana.ui.resources.preset_not_saved_storage_detail
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.save_changes_first
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val HeaderTile = 56.dp
private val HeaderIcon = 28.dp
private val SkeletonName = 180.dp
private val SkeletonMeta = 120.dp
private val SkeletonButton = 40.dp
private val SkeletonPanel = 220.dp

class PresetPageActions(
  val onBack: () -> Unit,
  val onIntent: (PresetIntent) -> Unit,
  val onOpen: (PresetPart) -> Unit,
  val onExport: () -> Unit,
)

@Composable
internal fun rememberPresetPage(part: PresetPart): PresetViewModel {
  val viewModel: PresetViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val sessions: PresetSessions = koinInject()
  LaunchedEffect(viewModel, part.presetUuid) { viewModel.show(part.presetUuid) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state == PresetPageState.Gone) navigator.close(Destination.Library, part) }
  if (part is PresetRoute) {
    val name = (state as? PresetPageState.Shown)?.name
    val leavingMessage = name?.let { stringResource(Res.string.changes_not_saved, it) }
    GuardUnsavedChanges(part) {
      val id = part.presetUuid
      if (leavingMessage != null && sessions.hasChanges(id)) {
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
  var asking by rememberSaveable { mutableStateOf<GroupPrompt?>(null) }
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
  PresetPageFrame(actions.onBack, stringResource(Res.string.back_to_presets), modifier) {
    when (state) {
      PresetPageState.Loading, PresetPageState.Gone -> Skeleton { PageSkeleton() }
      PresetPageState.CouldNotLoad ->
        RetryNote(stringResource(Res.string.preset_error), Res.string.preset_error_detail) {
          actions.onIntent(PresetIntent.Retry)
        }
      is PresetPageState.Shown -> ShownPage(state, actions, onAskGroup)
    }
  }
}

@Composable
internal fun PresetPageFrame(
  onBack: () -> Unit,
  backLabel: String,
  modifier: Modifier = Modifier,
  backOnEveryWidth: Boolean = false,
  content: @Composable () -> Unit,
) {
  val spacing = GlorianaTheme.spacing
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (onWorkbench) spacing.s20 else spacing.s16),
  ) {
    if (!onWorkbench || backOnEveryWidth) BackButton(backLabel, onBack)
    content()
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
    Box(Modifier.padding(bottom = spacing.s8)) { SaveButton(page, actions.onIntent) }
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
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  val content = page.content
  Row(
    Modifier.padding(top = spacing.s8, bottom = spacing.s16),
    horizontalArrangement = Arrangement.spacedBy(spacing.s14),
  ) {
    Box(
      Modifier.size(HeaderTile).background(colors.surface2, GlorianaTheme.shapes.panel),
      contentAlignment = Alignment.Center,
    ) {
      Icon(painterResource(Res.drawable.ic_view_list), null, Modifier.size(HeaderIcon), tint = colors.textMuted)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
      Text(page.name, style = type.headlineLarge, color = colors.text)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s6)) {
        val on = content.entries.count { it.enabled }
        Text(entriesLine(content.entries.size, on), style = type.bodyMedium, color = colors.textMuted)
      }
    }
    PresetMoreMenu(page, actions)
  }
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
  GlorianaMoreMenu(stringResource(Res.string.preset_more, page.name), items)
}

@Composable
internal fun SaveButton(
  page: PresetPageState.Shown,
  onIntent: (PresetIntent) -> Unit,
  beforeSave: () -> Boolean = { true },
) {
  val saving = page.session.activity == PresetActivity.Saving
  GlorianaButton(
    stringResource(if (saving) Res.string.saving else Res.string.save),
    { if (beforeSave()) onIntent(PresetIntent.Save) },
    kind = ButtonKind.Primary,
    icon = Res.drawable.ic_check_20,
    enabled = page.canSave,
  )
}

@Composable
internal fun SaveNote(page: PresetPageState.Shown, modifier: Modifier = Modifier) {
  val note = page.session.note ?: return
  val text = when (note) {
    PresetNote.NotSavedStorage -> boldThenPlain(
      stringResource(Res.string.preset_not_saved_storage, page.name),
      stringResource(Res.string.preset_not_saved_storage_detail),
    )
    PresetNote.NotSaved -> boldThenPlain(
      stringResource(Res.string.preset_not_saved, page.name),
      stringResource(Res.string.preset_not_saved_detail),
    )
    PresetNote.NotDeleted -> return
  }
  NoteCard(modifier.padding(bottom = GlorianaTheme.spacing.s8)) { NoteLine(text) }
}

@Composable
private fun Outcome(page: PresetPageState.Shown, actions: PresetPageActions) {
  val spacing = GlorianaTheme.spacing
  Column(Modifier.fillMaxWidth()) {
    SaveNote(page)
    if (page.session.note == PresetNote.NotDeleted) {
      NoteCard(Modifier.padding(bottom = spacing.s8)) {
        NoteLine(
          boldThenPlain(
            stringResource(Res.string.preset_not_deleted, page.name),
            stringResource(Res.string.preset_not_deleted_detail),
          ),
        )
        GlorianaButton(
          stringResource(Res.string.retry),
          { actions.onIntent(PresetIntent.AskToDelete) },
          icon = Res.drawable.ic_refresh_20,
        )
      }
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

@Composable
private fun PageSkeleton() {
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s16)) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s14)) {
      SkeletonBlock(Modifier.size(HeaderTile))
      Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
        SkeletonBlock(Modifier.size(width = SkeletonName, height = spacing.s20))
        SkeletonBlock(Modifier.size(width = SkeletonMeta, height = spacing.s12))
      }
    }
    SkeletonBlock(Modifier.size(width = SkeletonName, height = SkeletonButton))
    SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanel), on = SkeletonSurface.Raised)
  }
}

package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.presets.PresetImportFailure
import dev.marzahn.gloriana.logic.presets.PresetSummary
import dev.marzahn.gloriana.ui.components.AddRow
import dev.marzahn.gloriana.ui.components.DefaultMark
import dev.marzahn.gloriana.ui.components.EmptyAction
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.components.ListRow
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.RowLine
import dev.marzahn.gloriana.ui.components.RowSelection
import dev.marzahn.gloriana.ui.components.RowTile
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.components.chevron
import dev.marzahn.gloriana.ui.components.listMotion
import dev.marzahn.gloriana.ui.files.FileChoice
import dev.marzahn.gloriana.ui.files.rememberFilePicker
import dev.marzahn.gloriana.ui.lists.DeleteFocus
import dev.marzahn.gloriana.ui.lists.ReorderRow
import dev.marzahn.gloriana.ui.lists.ReorderableColumn
import dev.marzahn.gloriana.ui.lists.ReorderableList
import dev.marzahn.gloriana.ui.lists.RowIdentity
import dev.marzahn.gloriana.ui.lists.RowSkeletons
import dev.marzahn.gloriana.ui.lists.UserOrderMoves
import dev.marzahn.gloriana.ui.lists.rememberReorderableList
import dev.marzahn.gloriana.ui.lists.rememberRowFocus
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.OpenPagesWhileShown
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.choose_another_file
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.create_preset
import dev.marzahn.gloriana.ui.resources.ic_add_20
import dev.marzahn.gloriana.ui.resources.ic_draft
import dev.marzahn.gloriana.ui.resources.ic_file_open
import dev.marzahn.gloriana.ui.resources.ic_view_list
import dev.marzahn.gloriana.ui.resources.import_importing
import dev.marzahn.gloriana.ui.resources.import_waiting
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.resources.preset_entries_on
import dev.marzahn.gloriana.ui.resources.preset_import_not_a_preset
import dev.marzahn.gloriana.ui.resources.preset_import_not_a_preset_detail
import dev.marzahn.gloriana.ui.resources.preset_import_too_large
import dev.marzahn.gloriana.ui.resources.presets_empty
import dev.marzahn.gloriana.ui.resources.presets_empty_text
import dev.marzahn.gloriana.ui.resources.presets_error
import dev.marzahn.gloriana.ui.resources.presets_error_detail
import dev.marzahn.gloriana.ui.resources.presets_nothing_picked
import dev.marzahn.gloriana.ui.resources.presets_nothing_picked_text
import dev.marzahn.gloriana.ui.resources.reason_cant_open
import dev.marzahn.gloriana.ui.resources.reason_cant_open_detail
import dev.marzahn.gloriana.ui.resources.reason_damaged_json
import dev.marzahn.gloriana.ui.resources.reason_not_saved
import dev.marzahn.gloriana.ui.resources.reason_not_saved_detail
import dev.marzahn.gloriana.ui.resources.reason_storage_full
import dev.marzahn.gloriana.ui.resources.reason_storage_full_detail
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

private val PresetFiles = FileChoice(listOf("json"), several = false)

class PresetsActions(
  val onCreate: () -> Unit = {},
  val onOpen: (Uuid) -> Unit = {},
  val onRetry: () -> Unit = {},
  val onDismissFailure: (ImportFailureNote) -> Unit = {},
)

@Composable
fun PresetsLibrary(kindPicker: @Composable () -> Unit, modifier: Modifier = Modifier) {
  val viewModel: PresetsViewModel = koinViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val navigator = LocalShellNavigator.current
  val picker = rememberFilePicker(PresetFiles) { files -> files.firstOrNull()?.let(viewModel::import) }
  OpenPagesWhileShown(Destination.Library, viewModel::openPagesWhileShown) { navigator.pickPreset(it) }
  val actions = PresetsActions(
    onCreate = picker::pick,
    onOpen = navigator::pickPreset,
    onRetry = viewModel::retry,
    onDismissFailure = viewModel::dismissFailure,
  )
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val deleted by viewModel.lastDeleted.collectAsStateWithLifecycle()
  val focus = DeleteFocus(deleted, viewModel::focused)
  PresetsContent(
    state,
    actions,
    navigator.shownPreset()?.takeIf { onWorkbench },
    kindPicker,
    viewModel.moves,
    modifier,
    focus,
  )
  state.imports.offer?.let { offer ->
    SamplerOfferSheet(offer, onPick = viewModel::pick, onCopy = viewModel::copy, onDecline = viewModel::decline)
  }
}

@Composable
internal fun PresetsContent(
  state: PresetsState,
  actions: PresetsActions,
  selected: Uuid?,
  kindPicker: @Composable () -> Unit,
  moves: UserOrderMoves<*>,
  modifier: Modifier = Modifier,
  focus: DeleteFocus = DeleteFocus(null) {},
) {
  val list = rememberReorderableList(moves, Uuid::toString)
  val content = if (state.isEmpty) {
    TopLevelContent.Empty(
      EmptyBlock(
        icon = Res.drawable.ic_view_list,
        heading = stringResource(Res.string.presets_empty),
        text = stringResource(Res.string.presets_empty_text),
        action = EmptyAction(stringResource(Res.string.create), Res.drawable.ic_add_20, actions.onCreate),
      ),
    )
  } else {
    val nothingPicked = EmptyBlock(
      icon = Res.drawable.ic_view_list,
      heading = stringResource(Res.string.presets_nothing_picked),
      text = stringResource(Res.string.presets_nothing_picked_text),
    )
    val presets = (state.listing as? PresetsListing.Loaded)?.presets.orEmpty()
    val reorder = list.control(stringResource(Res.string.library_kind_presets), presets.size)
    TopLevelContent.Items(nothingPicked, onCreate = actions.onCreate, reorder = reorder) { header ->
      PresetRows(state, actions, selected, header, focus, list)
    }
  }
  TopLevelScreen(Destination.Library, content, modifier, kindPicker)
}

private const val ADD_ROW_KEY = "add"

private val PresetIdentity = RowIdentity(PresetSummary::id, PresetSummary::name)

private fun LazyListScope.importRows(imports: PresetImportState, actions: PresetsActions, density: ListDensity) {
  items(imports.failures, key = { "failure-${it.importId}" }) { failure ->
    FailureNote(failure, actions, listMotion())
  }
  items(listOfNotNull(imports.running) + imports.waiting, key = { "import-${it.id}" }) { queued ->
    val line = if (queued == imports.running) {
      RowLine.Running(stringResource(Res.string.import_importing))
    } else {
      RowLine.Plain(stringResource(Res.string.import_waiting))
    }
    ListRow(
      queued.fileName,
      line,
      onClick = null,
      modifier = listMotion(),
      density = density,
      avatar = { RowTile(Res.drawable.ic_draft, density) },
    )
  }
}

@Composable
private fun PresetRows(
  state: PresetsState,
  actions: PresetsActions,
  selected: Uuid?,
  header: @Composable () -> Unit,
  focus: DeleteFocus,
  list: ReorderableList,
) {
  val inPane = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val density = if (inPane) ListDensity.Pane else ListDensity.Screen
  val spacing = GlorianaTheme.spacing
  val focusModifier = rememberFocusAfterDelete(state, focus, list)
  val presets = (state.listing as? PresetsListing.Loaded)?.presets.orEmpty()
  ReorderableColumn(
    list,
    presets,
    PresetIdentity,
    PaddingValues(spacing.s8, if (inPane) spacing.s8 else 0.dp, spacing.s8, spacing.s16),
    Modifier.fillMaxSize(),
  ) {
    item(key = "header", contentType = "header") { header() }
    importRows(state.imports, actions, density)
    when (val listing = state.listing) {
      PresetsListing.Loading -> item(key = "loading") { Skeleton { RowSkeletons() } }
      PresetsListing.CouldNotLoad -> item(key = "error") {
        RetryNote(
          stringResource(Res.string.presets_error),
          Res.string.presets_error_detail,
          onRetry = actions.onRetry,
        )
      }
      is PresetsListing.Loaded -> {
        reorderableRows { preset, row ->
          PresetRow(
            preset,
            preset.id == listing.defaultId,
            preset.id == selected,
            density,
            row,
            { actions.onOpen(preset.id) },
            row.modifier.then(focusModifier(preset.id.toString())),
          )
        }
        if (presets.isNotEmpty() && !reordering) {
          item(key = ADD_ROW_KEY) {
            AddRow(
              stringResource(Res.string.create_preset),
              actions.onCreate,
              listMotion().padding(top = spacing.s8).then(focusModifier(ADD_ROW_KEY)),
              density,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun rememberFocusAfterDelete(
  state: PresetsState,
  focus: DeleteFocus,
  list: ReorderableList,
): (String) -> Modifier {
  val presets = (state.listing as? PresetsListing.Loaded)?.presets
  val deleted = focus.deleted?.takeIf { gone -> presets != null && presets.none { it.id == gone.id } }
  val target = deleted?.let { gone -> presets?.getOrNull(gone.index)?.id?.toString() ?: ADD_ROW_KEY }
  return rememberRowFocus(list, target) { deleted?.let(focus.onFocus) }
}

@Composable
private fun PresetRow(
  preset: PresetSummary,
  isDefault: Boolean,
  selected: Boolean,
  density: ListDensity,
  row: ReorderRow,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ListRow(
    name = preset.name,
    line = RowLine.Plain(entriesLine(preset.entries, preset.entriesOn)),
    onClick = row.onClick(onOpen),
    modifier = modifier,
    selection = if (selected) RowSelection.Selected else RowSelection.Unselected,
    density = density,
    nameMark = { DefaultMark(isDefault) },
    trailing = row.trailing(density.chevron()),
    avatar = row.avatar { RowTile(Res.drawable.ic_view_list, density) },
  )
}

@Composable
fun entriesLine(entries: Int, on: Int): String =
  pluralStringResource(Res.plurals.preset_entries_on, entries, entries, on)

@Composable
private fun FailureNote(failure: ImportFailureNote, actions: PresetsActions, modifier: Modifier = Modifier) {
  val dismiss = { actions.onDismissFailure(failure) }
  NoteCard(modifier.padding(bottom = GlorianaTheme.spacing.s8), onDismiss = dismiss) {
    NoteLine(failure.text())
    GlorianaButton(stringResource(Res.string.choose_another_file), actions.onCreate, icon = Res.drawable.ic_file_open)
  }
}

private class FailureText(val bold: StringResource, val plain: StringResource?)

private val PresetImportFailure.text: FailureText
  get() = when (this) {
    PresetImportFailure.CantBeOpened ->
      FailureText(Res.string.reason_cant_open, Res.string.reason_cant_open_detail)
    PresetImportFailure.Damaged -> FailureText(Res.string.reason_damaged_json, null)
    PresetImportFailure.NotAPreset ->
      FailureText(Res.string.preset_import_not_a_preset, Res.string.preset_import_not_a_preset_detail)
    PresetImportFailure.TooLarge -> FailureText(Res.string.preset_import_too_large, null)
    PresetImportFailure.StorageFull ->
      FailureText(Res.string.reason_storage_full, Res.string.reason_storage_full_detail)
    PresetImportFailure.CouldNotSave ->
      FailureText(Res.string.reason_not_saved, Res.string.reason_not_saved_detail)
  }

@Composable
private fun ImportFailureNote.text(): AnnotatedString {
  val text = reason.text
  return boldThenPlain(stringResource(text.bold, fileName), text.plain?.let { stringResource(it) })
}

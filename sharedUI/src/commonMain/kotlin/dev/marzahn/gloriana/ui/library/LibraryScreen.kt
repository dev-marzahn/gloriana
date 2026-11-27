package dev.marzahn.gloriana.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.characters.CardFiles
import dev.marzahn.gloriana.ui.characters.CharacterRoute
import dev.marzahn.gloriana.ui.characters.FullScreenAvatar
import dev.marzahn.gloriana.ui.characters.pickCharacter
import dev.marzahn.gloriana.ui.components.EmptyAction
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.components.ErrorNote
import dev.marzahn.gloriana.ui.components.Segment
import dev.marzahn.gloriana.ui.components.SegmentedControl
import dev.marzahn.gloriana.ui.files.rememberFilePicker
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.LibraryKinds
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.navigation.showLibraryKind
import dev.marzahn.gloriana.ui.presets.PresetsLibrary
import dev.marzahn.gloriana.ui.presets.shownPreset
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.characters_error
import dev.marzahn.gloriana.ui.resources.characters_nothing_picked
import dev.marzahn.gloriana.ui.resources.characters_nothing_picked_text
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.ic_add_20
import dev.marzahn.gloriana.ui.resources.ic_auto_stories
import dev.marzahn.gloriana.ui.resources.ic_person_add
import dev.marzahn.gloriana.ui.resources.library_empty
import dev.marzahn.gloriana.ui.resources.library_empty_text
import dev.marzahn.gloriana.ui.resources.library_kind_characters
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

private const val COLUMNS = 3
private const val SKELETON_TILES = 6

class LibraryActions(
  val onCreate: () -> Unit = {},
  val onTile: (Uuid) -> Unit = {},
  val onRetry: () -> Unit = {},
  val onChooseFiles: () -> Unit = {},
  val onDismissNote: () -> Unit = {},
  val onAnswerNow: () -> Unit = {},
)

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
  val kinds: LibraryKinds = koinInject()
  val request by kinds.request.collectAsState()
  val navigator = LocalShellNavigator.current
  var picked by rememberSaveable { mutableStateOf(LibraryKind.Characters) }
  LaunchedEffect(request) {
    request?.let {
      picked = it
      kinds.handled(it)
    }
  }
  val kind = if (navigator.shownPreset() != null) LibraryKind.Presets else picked
  val kindPicker = @Composable { LibraryKindPicker(kind) { navigator.showLibraryKind(it, kinds) } }
  when (kind) {
    LibraryKind.Characters -> CharactersLibrary(kindPicker, modifier)
    LibraryKind.Presets -> PresetsLibrary(kindPicker, modifier)
  }
}

@Composable
private fun LibraryKindPicker(kind: LibraryKind, onPick: (LibraryKind) -> Unit) {
  val segments = LibraryKind.entries.map { Segment(it, stringResource(it.label)) }
  SegmentedControl(segments, kind, onPick)
}

private val LibraryKind.label: StringResource
  get() = when (this) {
    LibraryKind.Characters -> Res.string.library_kind_characters
    LibraryKind.Presets -> Res.string.library_kind_presets
  }

@Composable
private fun CharactersLibrary(
  kindPicker: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LibraryViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val navigator = LocalShellNavigator.current
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val picker = rememberFilePicker(CardFiles, viewModel::import)
  var creating by rememberSaveable { mutableStateOf(false) }
  var previewing by rememberSaveable { mutableStateOf<String?>(null) }
  var enlarged by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(viewModel, navigator) { viewModel.openPagesWhileShown { navigator.pickCharacter(it) } }
  val actions = LibraryActions(
    onCreate = { creating = true },
    onTile = { id -> if (onWorkbench) navigator.pickCharacter(id) else previewing = id.toString() },
    onRetry = viewModel::retry,
    onChooseFiles = picker::pick,
    onDismissNote = viewModel::dismissNote,
    onAnswerNow = {
      creating = false
      previewing = null
      viewModel.showQuestion()
    },
  )
  val selected = (navigator.stack(Destination.Library).lastOrNull() as? CharacterRoute)?.uuid?.takeIf { onWorkbench }
  LibraryContent(state, modifier, actions, selected, kindPicker)
  if (creating) {
    CreateSheet(onImport = { creating = false; picker.pick() }, onDismiss = { creating = false })
  }
  previewing?.let { id ->
    val preview by remember(id) { viewModel.preview(Uuid.parse(id)) }.collectAsState(null)
    val shown = preview
    if (shown != null) {
      PreviewSheet(
        shown,
        onOpen = { previewing = null; navigator.pickCharacter(shown.id) },
        onAvatar = { enlarged = true },
        onDismiss = { previewing = null },
      )
      val path = shown.avatarPath
      if (enlarged && path != null) FullScreenAvatar(shown.name, path) { enlarged = false }
    }
  }
  val noSheetOpen = !creating && previewing == null
  val question = (state as? LibraryState.Shown)?.imports?.question?.takeIf { it.showing && noSheetOpen }
  if (question != null) AlreadyInLibrarySheet(question.question, viewModel::answer)
}

@Composable
internal fun LibraryContent(
  state: LibraryState,
  modifier: Modifier = Modifier,
  actions: LibraryActions = LibraryActions(),
  selected: Uuid? = null,
  kindPicker: (@Composable () -> Unit)? = null,
) {
  val nothingPicked = EmptyBlock(
    icon = Res.drawable.ic_auto_stories,
    heading = stringResource(Res.string.characters_nothing_picked),
    text = stringResource(Res.string.characters_nothing_picked_text),
  )
  val content = when (state) {
    LibraryState.Empty -> TopLevelContent.Empty(
      EmptyBlock(
        icon = Res.drawable.ic_person_add,
        heading = stringResource(Res.string.library_empty),
        text = stringResource(Res.string.library_empty_text),
        action = EmptyAction(stringResource(Res.string.create), Res.drawable.ic_add_20, actions.onCreate),
      ),
    )
    LibraryState.Loading -> TopLevelContent.Items(nothingPicked, onCreate = actions.onCreate) { header ->
      Grid(header) { skeletons() }
    }
    LibraryState.CouldNotLoad -> TopLevelContent.Items(nothingPicked, onCreate = actions.onCreate) { header ->
      val error = stringResource(Res.string.characters_error)
      Grid(header) { wide("error") { ErrorNote(error, onRetry = actions.onRetry) } }
    }
    is LibraryState.Shown -> TopLevelContent.Items(nothingPicked, onCreate = actions.onCreate) { header ->
      Grid(header) { metrics -> shown(state, metrics, actions, selected) }
    }
  }
  TopLevelScreen(Destination.Library, content, modifier, kindPicker)
}

@Composable
private fun Grid(header: @Composable () -> Unit, content: LazyGridScope.(TileMetrics) -> Unit) {
  val inPane = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val spacing = GlorianaTheme.spacing
  val metrics = if (inPane) PaneTile else PhoneTile
  val margin = if (inPane) spacing.s8 else spacing.s16
  LazyVerticalGrid(
    columns = GridCells.Fixed(COLUMNS),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = margin, end = margin, bottom = margin, top = if (inPane) margin else 0.dp),
    horizontalArrangement = Arrangement.spacedBy(spacing.s8),
    verticalArrangement = Arrangement.spacedBy(spacing.s8),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") { header() }
    content(metrics)
  }
}

private fun LazyGridScope.wide(key: String, content: @Composable () -> Unit) {
  item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.skeletons() {
  items(SKELETON_TILES) { SkeletonTile() }
}

private fun LazyGridScope.shown(
  state: LibraryState.Shown,
  metrics: TileMetrics,
  actions: LibraryActions,
  selected: Uuid?,
) {
  state.imports.note?.let { note ->
    wide("note") { ImportNoteCard(note, actions.onChooseFiles, actions.onDismissNote) }
  }
  items(state.imports.running, key = { "running-${it.key}" }) { file ->
    RunningTileView(file, metrics, actions.onAnswerNow, Modifier.animateItem(fadeOutSpec = null))
  }
  items(state.tiles, key = { it.id.toString() }) { tile ->
    CharacterTileView(tile, tile.id == selected, metrics, { actions.onTile(tile.id) }, Modifier.animateItem())
  }
  if (state.tiles.isNotEmpty()) item(key = "create") { CreateTile(actions.onCreate, Modifier.animateItem()) }
}

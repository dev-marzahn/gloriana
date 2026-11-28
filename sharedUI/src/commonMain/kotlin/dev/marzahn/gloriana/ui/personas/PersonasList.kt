package dev.marzahn.gloriana.ui.personas

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.personas.Persona
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.ui.components.AddRow
import dev.marzahn.gloriana.ui.components.Avatar
import dev.marzahn.gloriana.ui.components.AvatarSize
import dev.marzahn.gloriana.ui.components.DefaultMark
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.components.ListRow
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.RowLine
import dev.marzahn.gloriana.ui.components.RowSelection
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.chevron
import dev.marzahn.gloriana.ui.components.listMotion
import dev.marzahn.gloriana.ui.lists.DeleteFocus
import dev.marzahn.gloriana.ui.lists.RowFocus
import dev.marzahn.gloriana.ui.lists.RowSkeletons
import dev.marzahn.gloriana.ui.lists.rememberRowFocus
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.create_persona
import dev.marzahn.gloriana.ui.resources.ic_person
import dev.marzahn.gloriana.ui.resources.personas_error
import dev.marzahn.gloriana.ui.resources.personas_error_detail
import dev.marzahn.gloriana.ui.resources.personas_nothing_picked
import dev.marzahn.gloriana.ui.resources.personas_nothing_picked_text
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

private const val HEADER_ROWS = 1

private const val ADD_ROW_KEY = "add"

class PersonasActions(
  val onCreate: () -> Unit = {},
  val onOpen: (Uuid) -> Unit = {},
  val onRetry: () -> Unit = {},
)

@Composable
fun PersonasLibrary(kindPicker: @Composable () -> Unit, modifier: Modifier = Modifier) {
  val viewModel: PersonasViewModel = koinViewModel()
  val listing by viewModel.listing.collectAsStateWithLifecycle()
  val navigator = LocalShellNavigator.current
  val ids: IdFactory = koinInject()
  val actions = PersonasActions(
    onCreate = { navigator.createPersona(ids) },
    onOpen = navigator::pickPersona,
    onRetry = viewModel::retry,
  )
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val deleted by viewModel.lastDeleted.collectAsStateWithLifecycle()
  val focus = DeleteFocus(deleted, viewModel::focused)
  PersonasContent(listing, actions, navigator.shownPersona()?.takeIf { onWorkbench }, kindPicker, modifier, focus)
}

@Composable
internal fun PersonasContent(
  listing: PersonasListing,
  actions: PersonasActions,
  selected: Uuid?,
  kindPicker: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  focus: DeleteFocus = DeleteFocus(null) {},
) {
  val nothingPicked = EmptyBlock(
    icon = Res.drawable.ic_person,
    heading = stringResource(Res.string.personas_nothing_picked),
    text = stringResource(Res.string.personas_nothing_picked_text),
  )
  val content = TopLevelContent.Items(nothingPicked, onCreate = actions.onCreate) { header ->
    PersonaRows(listing, actions, selected, header, focus)
  }
  TopLevelScreen(Destination.Library, content, modifier, kindPicker)
}

@Composable
private fun PersonaRows(
  listing: PersonasListing,
  actions: PersonasActions,
  selected: Uuid?,
  header: @Composable () -> Unit,
  focus: DeleteFocus,
) {
  val inPane = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val density = if (inPane) ListDensity.Pane else ListDensity.Screen
  val spacing = GlorianaTheme.spacing
  val listState = rememberLazyListState()
  val focusModifier = rememberFocusAfterDelete(listing, focus, listState)
  LazyColumn(
    Modifier.fillMaxSize(),
    state = listState,
    contentPadding = PaddingValues(spacing.s8, if (inPane) spacing.s8 else 0.dp, spacing.s8, spacing.s16),
  ) {
    item(key = "header", contentType = "header") { header() }
    when (listing) {
      PersonasListing.Loading -> item(key = "loading") { Skeleton { RowSkeletons() } }
      PersonasListing.CouldNotLoad -> item(key = "error") {
        RetryNote(
          stringResource(Res.string.personas_error),
          Res.string.personas_error_detail,
          onRetry = actions.onRetry,
        )
      }
      is PersonasListing.Loaded -> {
        items(listing.personas, key = { it.id.toString() }) { persona ->
          PersonaRow(
            persona,
            isDefault = persona.id == listing.defaultId,
            selected = persona.id == selected,
            density = density,
            onClick = { actions.onOpen(persona.id) },
            modifier = listMotion().then(focusModifier(persona.id.toString())),
          )
        }
        item(key = ADD_ROW_KEY) {
          AddRow(
            stringResource(Res.string.create_persona),
            actions.onCreate,
            listMotion().padding(top = spacing.s8).then(focusModifier(ADD_ROW_KEY)),
            density,
          )
        }
      }
    }
  }
}

@Composable
private fun rememberFocusAfterDelete(
  listing: PersonasListing,
  focus: DeleteFocus,
  listState: LazyListState,
): (String) -> Modifier {
  val personas = (listing as? PersonasListing.Loaded)?.personas
  val deleted = focus.deleted?.takeIf { gone -> personas != null && personas.none { it.id == gone.id } }
  val target = deleted?.let { gone ->
    val index = gone.index.coerceAtMost(personas.orEmpty().size)
    RowFocus(personas?.getOrNull(index)?.id?.toString() ?: ADD_ROW_KEY, HEADER_ROWS + index)
  }
  return rememberRowFocus(listState, target) { deleted?.let(focus.onFocus) }
}

@Composable
private fun PersonaRow(
  persona: Persona,
  isDefault: Boolean,
  selected: Boolean,
  density: ListDensity,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val fields = persona.fields
  ListRow(
    name = fields.name,
    line = fields.personaLabel.takeIf { it.isNotBlank() }?.let(RowLine::Plain),
    onClick = onClick,
    modifier = modifier,
    selection = if (selected) RowSelection.Selected else RowSelection.Unselected,
    density = density,
    nameMark = { DefaultMark(isDefault) },
    trailing = density.chevron(),
    avatar = {
      Avatar(persona.id, fields.name, if (density == ListDensity.Pane) AvatarSize.ListPane else AvatarSize.List)
    },
  )
}

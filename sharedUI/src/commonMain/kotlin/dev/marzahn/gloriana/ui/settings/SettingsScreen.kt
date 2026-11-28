package dev.marzahn.gloriana.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.logic.connection.api.hostOf
import dev.marzahn.gloriana.ui.components.AddRow
import dev.marzahn.gloriana.ui.components.EmptyAction
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.components.EmptyState
import dev.marzahn.gloriana.ui.components.ListDensity
import dev.marzahn.gloriana.ui.components.ListRow
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.RowLine
import dev.marzahn.gloriana.ui.components.RowSelection
import dev.marzahn.gloriana.ui.components.RowTile
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.connections.ConnectionMoreMenu
import dev.marzahn.gloriana.ui.connections.ConnectionRoute
import dev.marzahn.gloriana.ui.connections.CreateConnectionSheet
import dev.marzahn.gloriana.ui.connections.DefaultMark
import dev.marzahn.gloriana.ui.connections.ModelSettingsRoute
import dev.marzahn.gloriana.ui.connections.Opening
import dev.marzahn.gloriana.ui.connections.PassphraseRoute
import dev.marzahn.gloriana.ui.connections.label
import dev.marzahn.gloriana.ui.connections.secretsLine
import dev.marzahn.gloriana.ui.lists.RowFocus
import dev.marzahn.gloriana.ui.lists.RowSkeletons
import dev.marzahn.gloriana.ui.lists.rememberRowFocus
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.navigation.replaceTop
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.connections_empty
import dev.marzahn.gloriana.ui.resources.connections_empty_text
import dev.marzahn.gloriana.ui.resources.connections_error
import dev.marzahn.gloriana.ui.resources.connections_error_detail
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.create_connection
import dev.marzahn.gloriana.ui.resources.ic_add_20
import dev.marzahn.gloriana.ui.resources.ic_chevron_right
import dev.marzahn.gloriana.ui.resources.ic_hub
import dev.marzahn.gloriana.ui.resources.ic_hub_20
import dev.marzahn.gloriana.ui.resources.ic_password_20
import dev.marzahn.gloriana.ui.resources.ic_settings
import dev.marzahn.gloriana.ui.resources.ic_tune_20
import dev.marzahn.gloriana.ui.resources.joined_by_dot
import dev.marzahn.gloriana.ui.resources.model_settings
import dev.marzahn.gloriana.ui.resources.model_settings_row
import dev.marzahn.gloriana.ui.resources.model_settings_row_short
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.resources.passphrase_row
import dev.marzahn.gloriana.ui.resources.passphrase_row_short
import dev.marzahn.gloriana.ui.resources.settings_group_connections
import dev.marzahn.gloriana.ui.resources.settings_group_every_connection
import dev.marzahn.gloriana.ui.resources.settings_group_keys
import dev.marzahn.gloriana.ui.resources.settings_nothing_picked
import dev.marzahn.gloriana.ui.resources.settings_nothing_picked_text
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val ChevronSize = 24.dp

class SettingsActions(
  val onOpen: (NavKey) -> Unit,
  val onCreate: () -> Unit,
  val onIntent: (SettingsIntent) -> Unit,
)

private sealed interface SettingsItem {
  val key: String

  data class Group(val title: StringResource) : SettingsItem {
    override val key = "group-$title"
  }

  data object Notes : SettingsItem {
    override val key = "notes"
  }

  data object Loading : SettingsItem {
    override val key = "loading"
  }

  data object LoadFailed : SettingsItem {
    override val key = "load-failed"
  }

  data class Connection(val row: ConnectionRowState) : SettingsItem {
    override val key = "connection-${row.id}"
  }

  data object Add : SettingsItem {
    override val key = "add"
  }

  data object Empty : SettingsItem {
    override val key = "empty"
  }

  data object ModelSettings : SettingsItem {
    override val key = "model-settings"
  }

  data object Passphrase : SettingsItem {
    override val key = "passphrase"
  }
}

private fun SettingsState.items(inPane: Boolean): List<SettingsItem> = buildList {
  add(SettingsItem.Group(Res.string.settings_group_connections))
  add(SettingsItem.Notes)
  when (val list = connections) {
    ConnectionsList.Loading -> add(SettingsItem.Loading)
    ConnectionsList.CouldNotLoad -> add(SettingsItem.LoadFailed)
    is ConnectionsList.Loaded -> {
      list.rows.forEach { add(SettingsItem.Connection(it)) }
      add(if (list.rows.isNotEmpty() || inPane) SettingsItem.Add else SettingsItem.Empty)
    }
  }
  add(SettingsItem.Group(Res.string.settings_group_every_connection))
  add(SettingsItem.ModelSettings)
  if (showsPassphrase) {
    add(SettingsItem.Group(Res.string.settings_group_keys))
    add(SettingsItem.Passphrase)
  }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
  val viewModel: SettingsViewModel = koinViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val navigator = LocalShellNavigator.current
  var creating by rememberSaveable { mutableStateOf(false) }
  val actions = SettingsActions(
    onOpen = { navigator.replaceTop(Destination.Settings, it) },
    onCreate = { creating = true },
    onIntent = viewModel::on,
  )
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val selected = navigator.stack(Destination.Settings).drop(1).firstOrNull()?.takeIf { onWorkbench }
  SettingsContent(state, actions, selected, modifier)
  if (creating) {
    CreateConnectionSheet(
      onPick = { start ->
        creating = false
        navigator.replaceTop(Destination.Settings, ConnectionRoute.New(start, Opening.fresh()))
      },
      onDismiss = { creating = false },
    )
  }
  SettingsSheets(state, viewModel::on)
}

@Composable
internal fun SettingsContent(
  state: SettingsState,
  actions: SettingsActions,
  selected: NavKey?,
  modifier: Modifier = Modifier,
) {
  val empty = (state.connections as? ConnectionsList.Loaded)?.rows?.isEmpty() == true
  val nothingPicked = if (empty) {
    connectionsEmptyBlock(actions.onCreate)
  } else {
    EmptyBlock(
      Res.drawable.ic_settings,
      stringResource(Res.string.settings_nothing_picked),
      stringResource(Res.string.settings_nothing_picked_text),
    )
  }
  val content = TopLevelContent.Items(nothingPicked, onCreate = actions.onCreate.takeUnless { empty }) { header ->
    SettingsList(state, actions, selected, header)
  }
  TopLevelScreen(Destination.Settings, content, modifier)
}

@Composable
private fun connectionsEmptyBlock(onCreate: () -> Unit) = EmptyBlock(
  Res.drawable.ic_hub,
  stringResource(Res.string.connections_empty),
  stringResource(Res.string.connections_empty_text),
  EmptyAction(stringResource(Res.string.create), Res.drawable.ic_add_20, onCreate),
)

private class ListScope(
  val state: SettingsState,
  val actions: SettingsActions,
  val selected: NavKey?,
  val density: ListDensity,
  val focus: (String) -> Modifier,
)

@Composable
private fun SettingsList(
  state: SettingsState,
  actions: SettingsActions,
  selected: NavKey?,
  header: @Composable () -> Unit,
) {
  val inPane = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  val spacing = GlorianaTheme.spacing
  val items = state.items(inPane)
  val listState = rememberLazyListState()
  val focusedKey = (state.focus as? ListFocus.Row)?.let { focus ->
    val rows = items.filterIsInstance<SettingsItem.Connection>()
    (rows.getOrNull(focus.index) ?: items.firstOrNull { it == SettingsItem.Add || it == SettingsItem.Empty })?.key
  }
  val focusTarget = focusedKey?.let { key -> RowFocus(key, items.indexOfFirst { it.key == key } + 1) }
  val focus = rememberRowFocus(listState, focusTarget) { actions.onIntent(SettingsIntent.Focused) }
  val density = if (inPane) ListDensity.Pane else ListDensity.Screen
  val scope = ListScope(state, actions, selected, density, focus)
  LazyColumn(
    Modifier.fillMaxSize(),
    state = listState,
    contentPadding = PaddingValues(spacing.s8, if (inPane) spacing.s8 else 0.dp, spacing.s8, spacing.s16),
  ) {
    item(key = "header", contentType = "header") { header() }
    items(items, key = { it.key }) { item -> Item(scope, item) }
  }
}

@Composable
private fun LazyItemScope.listMotion(): Modifier {
  val motion = GlorianaTheme.motion
  return Modifier.animateItem(
    fadeInSpec = motion.enter.tween(),
    placementSpec = motion.standard.tween(),
    fadeOutSpec = motion.exit.tween(),
  )
}

@Composable
private fun ListScope.focusModifier(item: SettingsItem): Modifier = focus(item.key)

@Composable
private fun LazyItemScope.Item(scope: ListScope, item: SettingsItem) = with(scope) {
  val spacing = GlorianaTheme.spacing
  when (item) {
    is SettingsItem.Group -> Text(
      stringResource(item.title),
      Modifier.padding(start = spacing.s12, top = spacing.s14, bottom = spacing.s6),
      style = GlorianaTheme.type.material.labelLarge,
      color = GlorianaTheme.colors.textSubtle,
    )
    SettingsItem.Notes -> ConnectionNotes(state, actions.onIntent)
    SettingsItem.Loading -> Skeleton { RowSkeletons() }
    SettingsItem.LoadFailed -> RetryNote(
      stringResource(Res.string.connections_error),
      Res.string.connections_error_detail,
    ) { actions.onIntent(SettingsIntent.Retry) }
    is SettingsItem.Connection -> {
      val route = ConnectionRoute.Saved(item.row.id)
      ConnectionRow(
        item.row,
        selected == route,
        density,
        actions,
        { actions.onOpen(route) },
        listMotion().then(focusModifier(item)),
      )
    }
    SettingsItem.Add -> AddRow(
      stringResource(Res.string.create_connection),
      actions.onCreate,
      listMotion().padding(top = spacing.s8).then(focusModifier(item)),
      density,
    )
    SettingsItem.Empty -> EmptyState(
      connectionsEmptyBlock(actions.onCreate),
      Modifier.fillMaxWidth().padding(vertical = spacing.s24),
      actionModifier = focusModifier(item),
    )
    SettingsItem.ModelSettings -> DestinationRow(
      stringResource(Res.string.model_settings),
      stringResource(
        if (density == ListDensity.Pane) Res.string.model_settings_row_short else Res.string.model_settings_row,
      ),
      Res.drawable.ic_tune_20,
      selected == ModelSettingsRoute,
      density,
    ) { actions.onOpen(ModelSettingsRoute) }
    SettingsItem.Passphrase -> DestinationRow(
      stringResource(Res.string.passphrase),
      stringResource(if (density == ListDensity.Pane) Res.string.passphrase_row_short else Res.string.passphrase_row),
      Res.drawable.ic_password_20,
      selected == PassphraseRoute,
      density,
    ) { actions.onOpen(PassphraseRoute) }
  }
}

@Composable
private fun ConnectionRow(
  row: ConnectionRowState,
  selected: Boolean,
  density: ListDensity,
  actions: SettingsActions,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val fields = row.connection.fields
  val line = secretsLine(row.secrets, short = density == ListDensity.Pane)
    ?: RowLine.Plain(stringResource(Res.string.joined_by_dot, fields.model, hostOf(fields.baseUrl)))
  ListRow(
    name = fields.name,
    line = line,
    onClick = onClick,
    modifier = modifier,
    selection = if (selected) RowSelection.Selected else RowSelection.Unselected,
    density = density,
    nameMark = { DefaultMark(row.isDefault) },
    thirdLine = stringResource(fields.format.label),
    trailing = {
      ConnectionMoreMenu(
        fields.name,
        row.isDefault,
        onMakeDefault = { actions.onIntent(SettingsIntent.MakeDefault(row)) },
        onDelete = { actions.onIntent(SettingsIntent.AskToDelete(row)) },
      )
    },
    avatar = { RowTile(Res.drawable.ic_hub_20, density) },
  )
}

@Composable
private fun DestinationRow(
  name: String,
  line: String,
  icon: DrawableResource,
  selected: Boolean,
  density: ListDensity,
  onClick: () -> Unit,
) {
  ListRow(
    name = name,
    line = RowLine.Plain(line),
    onClick = onClick,
    selection = if (selected) RowSelection.Selected else RowSelection.Unselected,
    density = density,
    trailing = if (density == ListDensity.Pane) {
      null
    } else {
      {
        Icon(
          painterResource(Res.drawable.ic_chevron_right),
          null,
          Modifier.size(ChevronSize),
          tint = GlorianaTheme.colors.textMuted,
        )
      }
    },
    avatar = { RowTile(icon, density) },
  )
}

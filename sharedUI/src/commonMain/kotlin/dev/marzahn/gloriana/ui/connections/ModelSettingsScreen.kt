package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.ui.components.BackButton
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.components.SkeletonSurface
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.GuardUnsavedChanges
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.UnsavedPage
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.all_formats
import dev.marzahn.gloriana.ui.resources.back_to_settings
import dev.marzahn.gloriana.ui.resources.discard_model_settings
import dev.marzahn.gloriana.ui.resources.formats_two
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.ic_tune_20
import dev.marzahn.gloriana.ui.resources.joined_by_dot
import dev.marzahn.gloriana.ui.resources.model_settings
import dev.marzahn.gloriana.ui.resources.model_settings_error
import dev.marzahn.gloriana.ui.resources.model_settings_error_detail
import dev.marzahn.gloriana.ui.resources.model_settings_footer
import dev.marzahn.gloriana.ui.resources.model_settings_intro
import dev.marzahn.gloriana.ui.resources.model_settings_not_saved
import dev.marzahn.gloriana.ui.resources.model_settings_not_saved_storage
import dev.marzahn.gloriana.ui.resources.not_saved_detail
import dev.marzahn.gloriana.ui.resources.not_saved_storage_detail
import dev.marzahn.gloriana.ui.resources.range_groups_two
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.resources.section_samplers
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SkeletonButton = 40.dp
private val SkeletonButtonWidth = 96.dp
private val SkeletonPanel = 260.dp

class ModelSettingsActions(
  val onBack: () -> Unit,
  val onRetry: () -> Unit,
  val onEdit: (Sampler, String) -> Unit,
  val onSave: () -> Unit,
)

@Composable
fun ModelSettingsScreen(modifier: Modifier = Modifier) {
  val viewModel: ModelSettingsViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val focus = remember { Sampler.entries.associateWith { FocusRequester() } }
  LaunchedEffect(viewModel) { viewModel.focusRequests.collect { focus.getValue(it).requestFocus() } }
  val unsaved = (state as? ModelSettingsState.Shown)?.takeIf { it.copy.hasChanges }?.let {
    UnsavedPage(
      stringResource(Res.string.discard_model_settings),
      stringResource(Res.string.model_settings),
      viewModel::discard,
    )
  }
  GuardUnsavedChanges(ModelSettingsRoute, unsaved)
  val actions = ModelSettingsActions(
    onBack = { navigator.pop(Destination.Settings) },
    onRetry = viewModel::retry,
    onEdit = viewModel::edit,
    onSave = viewModel::save,
  )
  ModelSettingsContent(state, actions, focus, modifier)
}

@Composable
internal fun ModelSettingsContent(
  state: ModelSettingsState,
  actions: ModelSettingsActions,
  focus: Map<Sampler, FocusRequester>,
  modifier: Modifier = Modifier,
) {
  val spacing = GlorianaTheme.spacing
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (onWorkbench) spacing.s20 else spacing.s16),
    verticalArrangement = Arrangement.spacedBy(spacing.s16),
  ) {
    if (!onWorkbench) BackButton(stringResource(Res.string.back_to_settings), actions.onBack)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
      Text(stringResource(Res.string.model_settings), style = type.headlineLarge, color = colors.text)
      if (state !is ModelSettingsState.CouldNotLoad) {
        Text(stringResource(Res.string.model_settings_intro), style = type.bodyMedium, color = colors.textMuted)
      }
    }
    when (state) {
      ModelSettingsState.Loading -> Skeleton { ModelSettingsSkeleton() }
      ModelSettingsState.CouldNotLoad ->
        RetryNote(stringResource(Res.string.model_settings_error), Res.string.model_settings_error_detail) {
          actions.onRetry()
        }
      is ModelSettingsState.Shown -> ShownSettings(state, actions, focus)
    }
  }
}

@Composable
private fun ShownSettings(
  state: ModelSettingsState.Shown,
  actions: ModelSettingsActions,
  focus: Map<Sampler, FocusRequester>,
) {
  GlorianaButton(
    stringResource(if (state.saving) Res.string.saving else Res.string.save),
    actions.onSave,
    kind = ButtonKind.Primary,
    icon = Res.drawable.ic_check_20,
    enabled = !state.saving && state.copy.hasChanges,
  )
  state.note?.let { note ->
    NoteCard {
      NoteLine(
        when (note) {
          ModelSettingsNote.NotSavedStorage -> boldThenPlain(
            stringResource(Res.string.model_settings_not_saved_storage),
            stringResource(Res.string.not_saved_storage_detail),
          )
          ModelSettingsNote.NotSaved -> boldThenPlain(
            stringResource(Res.string.model_settings_not_saved),
            stringResource(Res.string.not_saved_detail),
          )
        },
      )
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    Panel(
      Res.drawable.ic_tune_20,
      stringResource(Res.string.section_samplers),
      Sampler.entries.map { sampler ->
        {
          SamplerRow(
            sampler = sampler,
            text = state.copy.draft[sampler],
            hint = formatsHint(sampler),
            error = state.errors[sampler],
            onChange = { actions.onEdit(sampler, it) },
            fieldModifier = Modifier.focusRequester(focus.getValue(sampler)),
          )
        }
      },
    )
    FieldNote(stringResource(Res.string.model_settings_footer), null)
  }
}

@Composable
private fun formatsHint(sampler: Sampler): String {
  val groups = sampler.rangesByFormat()
  val single = groups.singleOrNull()
  if (single != null && single.formats.size == ApiFormat.entries.size) {
    return stringResource(Res.string.joined_by_dot, stringResource(Res.string.all_formats), rangeText(single.range))
  }
  val parts = groups.map { group ->
    stringResource(
      Res.string.joined_by_dot,
      joined(group.formats.map { stringResource(it.label) }, ListKind.Formats),
      rangeText(group.range),
    )
  }
  return joined(parts, ListKind.RangeGroups)
}

private enum class ListKind { Formats, RangeGroups }

@Composable
private fun joined(items: List<String>, kind: ListKind): String {
  val pair = when (kind) {
    ListKind.Formats -> Res.string.formats_two
    ListKind.RangeGroups -> Res.string.range_groups_two
  }
  return items.fold("") { joined, item -> if (joined.isEmpty()) item else stringResource(pair, joined, item) }
}

@Composable
private fun ModelSettingsSkeleton() {
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    SkeletonBlock(Modifier.width(SkeletonButtonWidth).height(SkeletonButton))
    SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanel), on = SkeletonSurface.Raised)
  }
}

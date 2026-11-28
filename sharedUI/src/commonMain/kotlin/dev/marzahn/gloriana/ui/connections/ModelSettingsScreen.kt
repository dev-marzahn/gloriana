package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.ui.components.DetailPage
import dev.marzahn.gloriana.ui.components.DetailSkeleton
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.NotSavedCopy
import dev.marzahn.gloriana.ui.components.NotSavedLine
import dev.marzahn.gloriana.ui.components.PageBack
import dev.marzahn.gloriana.ui.components.PageTitle
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.SaveButton
import dev.marzahn.gloriana.ui.components.SkeletonHeader
import dev.marzahn.gloriana.ui.components.joinedInPairs
import dev.marzahn.gloriana.ui.components.notSavedText
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.GuardUnsavedChanges
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.UnsavedPage
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.all_formats
import dev.marzahn.gloriana.ui.resources.back_to_settings
import dev.marzahn.gloriana.ui.resources.discard_model_settings
import dev.marzahn.gloriana.ui.resources.discard_title
import dev.marzahn.gloriana.ui.resources.formats_two
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
import dev.marzahn.gloriana.ui.resources.section_samplers
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ModelSettingsScreen(modifier: Modifier = Modifier) {
  val viewModel: ModelSettingsViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val focus = remember { Sampler.entries.associateWith { FocusRequester() } }
  LaunchedEffect(viewModel) { viewModel.focusRequests.collect { focus.getValue(it).requestFocus() } }
  val unsaved = (state as? ModelSettingsState.Shown)?.takeIf { it.unsaved.hasChanges }?.let {
    UnsavedPage(
      stringResource(Res.string.discard_title),
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
  DetailPage(PageBack(stringResource(Res.string.back_to_settings), actions.onBack), modifier) {
    PageTitle(
      stringResource(Res.string.model_settings),
      intro = stringResource(Res.string.model_settings_intro).takeUnless { state is ModelSettingsState.CouldNotLoad },
    )
    when (state) {
      ModelSettingsState.Loading -> DetailSkeleton(SkeletonHeader.None, buttons = 1)
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
  SaveButton(state.unsaved.isSaving, state.canSave, actions.onSave)
  state.note?.let { failure -> NoteCard { NoteLine(notSavedText(failure, ModelSettingsNotSaved)) } }
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    Panel(
      Res.drawable.ic_tune_20,
      stringResource(Res.string.section_samplers),
      Sampler.entries.map { sampler ->
        {
          SamplerRow(
            sampler = sampler,
            text = state.unsaved.draft[sampler],
            hint = formatsHint(sampler),
            errorText = state.errors[sampler]?.text(),
            onChange = { actions.onEdit(sampler, it) },
            fieldModifier = Modifier.focusRequester(focus.getValue(sampler)),
          )
        }
      },
    )
    FieldNote(AnnotatedString(stringResource(Res.string.model_settings_footer)), null)
  }
}

private val ModelSettingsNotSaved = NotSavedCopy(
  NotSavedLine(Res.string.model_settings_not_saved_storage, Res.string.not_saved_storage_detail),
  NotSavedLine(Res.string.model_settings_not_saved, Res.string.not_saved_detail),
)

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
      joinedInPairs(group.formats.map { stringResource(it.label) }, Res.string.formats_two),
      rangeText(group.range),
    )
  }
  return joinedInPairs(parts, Res.string.range_groups_two)
}

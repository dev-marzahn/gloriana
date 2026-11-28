package dev.marzahn.gloriana.ui.personas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.Avatar
import dev.marzahn.gloriana.ui.components.AvatarSize
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.DefaultMark
import dev.marzahn.gloriana.ui.components.DetailHeader
import dev.marzahn.gloriana.ui.components.DetailPage
import dev.marzahn.gloriana.ui.components.DetailSkeleton
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaConfirmation
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.components.GlorianaMoreMenu
import dev.marzahn.gloriana.ui.components.NotSavedCopy
import dev.marzahn.gloriana.ui.components.NotSavedNote
import dev.marzahn.gloriana.ui.components.PageBack
import dev.marzahn.gloriana.ui.components.PageName
import dev.marzahn.gloriana.ui.components.RefusedAsDefaultCopy
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.SaveButton
import dev.marzahn.gloriana.ui.components.SkeletonHeader
import dev.marzahn.gloriana.ui.lists.DeleteImpact
import dev.marzahn.gloriana.ui.lists.DeleteItemCopy
import dev.marzahn.gloriana.ui.lists.ItemDeleteQuestion
import dev.marzahn.gloriana.ui.lists.ItemWorkNotes
import dev.marzahn.gloriana.ui.lists.deleteMenuItem
import dev.marzahn.gloriana.ui.lists.makeDefaultMenuItem
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.GuardUnsavedChanges
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.UnsavedPage
import dev.marzahn.gloriana.ui.navigation.discardConfirmation
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.back_to_personas
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.delete_persona
import dev.marzahn.gloriana.ui.resources.delete_persona_chats
import dev.marzahn.gloriana.ui.resources.discard_new_persona
import dev.marzahn.gloriana.ui.resources.discard_new_persona_unnamed
import dev.marzahn.gloriana.ui.resources.discard_persona_title
import dev.marzahn.gloriana.ui.resources.discard_title
import dev.marzahn.gloriana.ui.resources.edit
import dev.marzahn.gloriana.ui.resources.ic_edit
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.new_persona
import dev.marzahn.gloriana.ui.resources.not_saved_yet
import dev.marzahn.gloriana.ui.resources.persona_default_cant_delete
import dev.marzahn.gloriana.ui.resources.persona_delete_text
import dev.marzahn.gloriana.ui.resources.persona_delete_text_no_chats
import dev.marzahn.gloriana.ui.resources.persona_error
import dev.marzahn.gloriana.ui.resources.persona_error_detail
import dev.marzahn.gloriana.ui.resources.persona_not_saved_detail
import dev.marzahn.gloriana.ui.resources.persona_not_saved_storage_detail
import dev.marzahn.gloriana.ui.resources.persona_refused_default
import dev.marzahn.gloriana.ui.resources.persona_refused_default_detail
import dev.marzahn.gloriana.ui.resources.quit_name_new_persona
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val PersonaRefusedAsDefault =
  RefusedAsDefaultCopy(Res.string.persona_refused_default, Res.string.persona_refused_default_detail)

private val DeletePersonaCopy = DeleteItemCopy(Res.string.delete_persona, Res.string.persona_default_cant_delete)

private val PersonaSaveFailures =
  NotSavedCopy.forANamedItem(Res.string.persona_not_saved_storage_detail, Res.string.persona_not_saved_detail)

@Composable
fun PersonaScreen(route: PersonaRoute, modifier: Modifier = Modifier) {
  val viewModel: PersonaViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val sessions: PersonaSessions = koinInject()
  val nameFocus = remember { FocusRequester() }
  var askingToCancel by rememberSaveable(route) { mutableStateOf(false) }
  LaunchedEffect(viewModel, route) { viewModel.show(route) }
  LaunchedEffect(viewModel) { viewModel.focusName.collect { nameFocus.requestFocus() } }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val shown = state as? PersonaPageState.Shown
  LaunchedEffect(state) {
    if (state == PersonaPageState.Gone) navigator.closeWithoutAsking(Destination.Library, route)
    shown?.route?.takeIf { route is PersonaRoute.New && it == PersonaRoute.Saved(route.opening.id) }?.let { saved ->
      navigator.replaceWithoutAsking(Destination.Library, shown = route, with = saved)
    }
  }
  DisposableEffect(route) {
    onDispose { if (route !in navigator.stack(Destination.Library)) sessions.closed(route) }
  }
  val leaving = shown?.let { leaveCopy(it) }
  GuardUnsavedChanges(route) {
    val page = viewModel.state.value as? PersonaPageState.Shown
    leaving?.takeIf { page?.hasChanges == true }?.let { copy ->
      UnsavedPage(copy.title, copy.message, copy.quittingName, discard = { sessions.discard(route) })
    }
  }
  val discardEditing = {
    askingToCancel = false
    when (route) {
      is PersonaRoute.New -> navigator.closeWithoutAsking(Destination.Library, route)
      is PersonaRoute.Saved -> viewModel.on(PersonaIntent.Discard)
    }
  }
  val actions = PersonaPageActions(
    onBack = { navigator.pop(Destination.Library) },
    onIntent = viewModel::on,
    onCancel = { if (shown?.hasChanges == true) askingToCancel = true else discardEditing() },
  )
  PersonaPageContent(state, actions, nameFocus, modifier)
  shown?.let { page -> PersonaDeleteQuestion(page, viewModel::on) }
  if (askingToCancel && leaving != null) {
    GlorianaConfirmation(
      discardConfirmation(leaving.title, leaving.message),
      onConfirm = discardEditing,
      onDismiss = { askingToCancel = false },
    )
  }
}

private class LeaveCopy(val title: String, val message: String, val quittingName: String)

@Composable
private fun leaveCopy(page: PersonaPageState.Shown): LeaveCopy {
  val typedName = page.fields.name.trim()
  return when (page.route) {
    is PersonaRoute.Saved -> LeaveCopy(
      title = stringResource(Res.string.discard_title),
      message = stringResource(Res.string.changes_not_saved, page.savedName),
      quittingName = page.savedName,
    )
    is PersonaRoute.New -> LeaveCopy(
      title = stringResource(Res.string.discard_persona_title),
      message = if (typedName.isEmpty()) {
        stringResource(Res.string.discard_new_persona_unnamed)
      } else {
        stringResource(Res.string.discard_new_persona, typedName)
      },
      quittingName = typedName.ifEmpty { stringResource(Res.string.quit_name_new_persona) },
    )
  }
}

@Composable
internal fun PersonaPageContent(
  state: PersonaPageState,
  actions: PersonaPageActions,
  nameFocus: FocusRequester,
  modifier: Modifier = Modifier,
) {
  DetailPage(PageBack(stringResource(Res.string.back_to_personas), actions.onBack), modifier) {
    when (state) {
      PersonaPageState.Loading, PersonaPageState.Gone -> DetailSkeleton(SkeletonHeader.Tile, buttons = 0)
      PersonaPageState.CouldNotLoad ->
        RetryNote(stringResource(Res.string.persona_error), Res.string.persona_error_detail) {
          actions.onIntent(PersonaIntent.Retry)
        }
      is PersonaPageState.Shown -> {
        Header(state, actions.onIntent)
        if (state.isEditing) EditingActions(state, actions)
        Outcome(state, actions.onIntent)
        PersonaPanels(state, PersonaFieldEditing(nameFocus, actions.onIntent).takeIf { state.isEditing })
      }
    }
  }
}

@Composable
private fun Header(page: PersonaPageState.Shown, onIntent: (PersonaIntent) -> Unit) {
  val shownName = page.fields.name.ifBlank { page.savedName }
  DetailHeader(
    tile = { Avatar(page.route.avatarId, shownName, AvatarSize.Header) },
    name = PageName.of(shownName, stringResource(Res.string.new_persona)),
    meta = {
      if (page.stored == null) {
        Text(
          stringResource(Res.string.not_saved_yet),
          style = GlorianaTheme.type.material.bodyMedium,
          color = GlorianaTheme.colors.textMuted,
        )
      }
      DefaultMark(page.isDefault)
    },
    actions = {
      if (page.stored != null && !page.isEditing) {
        GlorianaIconButton(Res.drawable.ic_edit, stringResource(Res.string.edit), {
          onIntent(PersonaIntent.StartEditing)
        })
        PersonaMoreMenu(page, onIntent)
      }
    },
  )
}

@Composable
private fun PersonaMoreMenu(page: PersonaPageState.Shown, onIntent: (PersonaIntent) -> Unit) {
  val items = listOfNotNull(
    makeDefaultMenuItem(page.isDefault) { onIntent(PersonaIntent.MakeDefault) },
    deleteMenuItem(DeletePersonaCopy, page.isDefault) { onIntent(PersonaIntent.AskToDelete) },
  )
  GlorianaMoreMenu(stringResource(Res.string.more_for, page.savedName), items)
}

@Composable
private fun EditingActions(page: PersonaPageState.Shown, actions: PersonaPageActions) {
  val saving = page.session.isSaving
  Row(horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    SaveButton(saving, page.canSave, { actions.onIntent(PersonaIntent.Save) })
    GlorianaButton(stringResource(Res.string.cancel), actions.onCancel, kind = ButtonKind.Ghost, enabled = !saving)
  }
}

@Composable
private fun Outcome(page: PersonaPageState.Shown, onIntent: (PersonaIntent) -> Unit) {
  val session = page.session
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    session.notSaved?.let { failure ->
      NotSavedNote(failure, PersonaSaveFailures, page.fields.name.trim().ifEmpty { page.savedName })
    }
    ItemWorkNotes(
      session.work,
      page.savedName,
      PersonaRefusedAsDefault,
      onMakeDefault = { onIntent(PersonaIntent.MakeDefault) },
      onAskToDelete = { onIntent(PersonaIntent.AskToDelete) },
    )
  }
}

@Composable
private fun PersonaDeleteQuestion(page: PersonaPageState.Shown, onIntent: (PersonaIntent) -> Unit) {
  val name = page.savedName
  ItemDeleteQuestion(
    page.session.work,
    name,
    lines = { impact ->
      when (impact) {
        DeleteImpact.NoChats -> listOf(stringResource(Res.string.persona_delete_text_no_chats, name))
        DeleteImpact.Unknown -> listOf(stringResource(Res.string.persona_delete_text, name))
        is DeleteImpact.MovesChats -> listOf(
          stringResource(Res.string.persona_delete_text, name),
          pluralStringResource(Res.plurals.delete_persona_chats, impact.chats, impact.chats, impact.defaultName),
        )
      }
    },
    onConfirm = { onIntent(PersonaIntent.ConfirmDelete) },
    onDismiss = { onIntent(PersonaIntent.CancelDelete) },
  )
}

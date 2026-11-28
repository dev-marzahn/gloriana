package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.DetailHeader
import dev.marzahn.gloriana.ui.components.DetailPage
import dev.marzahn.gloriana.ui.components.DetailSkeleton
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.InfoCard
import dev.marzahn.gloriana.ui.components.NotDeletedNote
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.PageBack
import dev.marzahn.gloriana.ui.components.PageName
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.SaveButton
import dev.marzahn.gloriana.ui.components.SkeletonHeader
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.components.textWith
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.GuardUnsavedChanges
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.UnsavedPage
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.back_to_settings
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.connection_error
import dev.marzahn.gloriana.ui.resources.connection_error_detail
import dev.marzahn.gloriana.ui.resources.discard_new_connection
import dev.marzahn.gloriana.ui.resources.followed_by_dot
import dev.marzahn.gloriana.ui.resources.ic_check_circle_20
import dev.marzahn.gloriana.ui.resources.ic_hub
import dev.marzahn.gloriana.ui.resources.ic_network_check_20
import dev.marzahn.gloriana.ui.resources.new_connection
import dev.marzahn.gloriana.ui.resources.not_saved_yet
import dev.marzahn.gloriana.ui.resources.quit_name_new_connection
import dev.marzahn.gloriana.ui.resources.test
import dev.marzahn.gloriana.ui.resources.test_failed
import dev.marzahn.gloriana.ui.resources.test_passed
import dev.marzahn.gloriana.ui.resources.testing
import dev.marzahn.gloriana.ui.resources.this_connection
import dev.marzahn.gloriana.ui.text.withOneDecimal
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.DurationUnit

private const val REPLY_LINES = 2

class ConnectionPageActions(val onBack: () -> Unit, val onIntent: (PageIntent) -> Unit)

class FocusRequesters {
  private val requesters = mutableMapOf<FormField, FocusRequester>()

  fun of(field: FormField): FocusRequester = requesters.getOrPut(field) { FocusRequester() }
}

@Composable
fun ConnectionScreen(route: ConnectionRoute, modifier: Modifier = Modifier) {
  val viewModel: ConnectionViewModel = koinViewModel()
  val navigator = LocalShellNavigator.current
  val focus = remember { FocusRequesters() }
  LaunchedEffect(viewModel, route) { viewModel.show(route) }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is PageEvent.Focus -> focus.of(event.field).requestFocus()
        is PageEvent.Saved -> navigator.replaceWithoutAsking(Destination.Settings, shown = route, with = event.route)
      }
    }
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) {
    if (state == ConnectionPageState.Gone) navigator.closeWithoutAsking(Destination.Settings, route)
  }
  val shown = when (val current = state) {
    is ConnectionPageState.Shown -> current
    ConnectionPageState.Loading, ConnectionPageState.CouldNotLoad, ConnectionPageState.Gone -> null
  }
  GuardUnsavedChanges(
    route,
    shown?.takeIf { it.unsaved.hasChanges }?.let { unsavedPage(it) { viewModel.on(PageIntent.Discard) } },
  )
  val actions = ConnectionPageActions(onBack = { navigator.pop(Destination.Settings) }, onIntent = viewModel::on)
  ConnectionContent(state, actions, focus, modifier)
  shown?.let { PageSheets(it, viewModel::on) }
}

@Composable
private fun unsavedPage(page: ConnectionPageState.Shown, discard: () -> Unit): UnsavedPage {
  val name = page.form.name.trim().ifEmpty { page.saved?.fields?.name.orEmpty() }
  return UnsavedPage(
    leavingMessage = if (name.isEmpty()) {
      stringResource(Res.string.discard_new_connection)
    } else {
      stringResource(Res.string.changes_not_saved, name)
    },
    quittingName = name.ifEmpty { stringResource(Res.string.quit_name_new_connection) },
    discard = discard,
  )
}

@Composable
internal fun ConnectionContent(
  state: ConnectionPageState,
  actions: ConnectionPageActions,
  focus: FocusRequesters,
  modifier: Modifier = Modifier,
) {
  DetailPage(PageBack(stringResource(Res.string.back_to_settings), actions.onBack), modifier) {
    when (state) {
      ConnectionPageState.Loading, ConnectionPageState.Gone -> DetailSkeleton(SkeletonHeader.Tile, buttons = 2)
      ConnectionPageState.CouldNotLoad ->
        RetryNote(stringResource(Res.string.connection_error), Res.string.connection_error_detail) {
          actions.onIntent(PageIntent.Retry)
        }
      is ConnectionPageState.Shown -> {
        Header(state, actions.onIntent)
        ActionsRow(state, actions.onIntent)
        OutcomeSlot(state, actions.onIntent)
        ConnectionPanels(state, FormEditing(state, focus, actions.onIntent))
      }
    }
  }
}

@Composable
private fun OutcomeSlot(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val standard = GlorianaTheme.motion.standard
  Column(Modifier.animateContentSize(standard.tween())) {
    AnimatedVisibility(
      page.test != null || page.note != null,
      enter = fadeIn(standard.tween()),
      exit = fadeOut(standard.tween()),
    ) {
      Box(Modifier.padding(bottom = GlorianaTheme.spacing.s8)) { Outcome(page, onIntent) }
    }
  }
}

@Composable
private fun Header(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val colors = GlorianaTheme.colors
  val saved = page.saved
  DetailHeader(
    Res.drawable.ic_hub,
    name = PageName.of(
      page.form.name.ifBlank { saved?.fields?.name.orEmpty() },
      placeholder = stringResource(Res.string.new_connection),
    ),
    meta = {
      val pieces = saved?.let {
        listOf(
          stringResource(Res.string.followed_by_dot, stringResource(it.fields.format.label)),
          hostOf(it.fields.baseUrl),
        )
      } ?: listOf(stringResource(Res.string.not_saved_yet))
      pieces.forEach { piece ->
        Text(
          piece,
          style = GlorianaTheme.type.material.bodyMedium,
          color = colors.textMuted,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
      DefaultMark(page.isDefault)
    },
    actions = {
      if (saved != null) {
        ConnectionMoreMenu(
          saved.fields.name,
          page.isDefault,
          onMakeDefault = { onIntent(PageIntent.MakeDefault) },
          onDelete = { onIntent(PageIntent.AskToDelete) },
        )
      }
    },
  )
}

@Composable
private fun ActionsRow(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val testing = page.activity == PageActivity.Testing
  Row(horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    SaveButton(page.unsaved.isSaving, page.canSave, { onIntent(PageIntent.Save) })
    GlorianaButton(
      stringResource(if (testing) Res.string.testing else Res.string.test),
      { onIntent(PageIntent.Test) },
      icon = Res.drawable.ic_network_check_20,
      enabled = page.isIdle,
      running = testing,
    )
  }
}

@Composable
private fun Outcome(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val name = page.form.name.trim().ifEmpty { stringResource(Res.string.this_connection) }
  val note = page.note
  when {
    note == PageNote.NotDeleted -> NotDeletedNote(name, onRetry = { onIntent(PageIntent.AskToDelete) })
    note != null -> NoteCard { NoteLine(note.text(name)) }
    else -> TestResult(page.test)
  }
}

@Composable
private fun TestResult(test: TestOutcome?) {
  when (test) {
    is TestOutcome.Passed ->
      PassedBox(
        stringResource(Res.string.test_passed, withOneDecimal(test.took.toDouble(DurationUnit.SECONDS)), test.reply),
      )
    is TestOutcome.Failed -> NoteCard {
      NoteLine(
        boldThenPlain(
          textWith(Res.string.test_failed, test.failure.reason()),
          test.failure.details(StepFor.Test),
        ),
      )
    }
    null -> Unit
  }
}

@Composable
private fun PassedBox(text: String) {
  InfoCard(Res.drawable.ic_check_circle_20) {
    Text(
      text,
      style = GlorianaTheme.type.material.bodyMedium,
      color = GlorianaTheme.colors.text,
      maxLines = REPLY_LINES,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

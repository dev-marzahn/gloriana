package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.connection.api.hostOf
import dev.marzahn.gloriana.ui.components.BackButton
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.InfoCard
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.RetryNote
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.components.SkeletonSurface
import dev.marzahn.gloriana.ui.components.boldThenPlain
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
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_check_circle_20
import dev.marzahn.gloriana.ui.resources.ic_hub
import dev.marzahn.gloriana.ui.resources.ic_network_check_20
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.new_connection
import dev.marzahn.gloriana.ui.resources.not_saved_yet
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.resources.quit_name_new_connection
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.resources.set_passphrase_and_save
import dev.marzahn.gloriana.ui.resources.set_passphrase_failed
import dev.marzahn.gloriana.ui.resources.set_passphrase_failed_detail
import dev.marzahn.gloriana.ui.resources.set_passphrase_text
import dev.marzahn.gloriana.ui.resources.set_passphrase_title
import dev.marzahn.gloriana.ui.resources.setting
import dev.marzahn.gloriana.ui.resources.test
import dev.marzahn.gloriana.ui.resources.test_failed
import dev.marzahn.gloriana.ui.resources.test_passed
import dev.marzahn.gloriana.ui.resources.testing
import dev.marzahn.gloriana.ui.resources.this_connection
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.DurationUnit

private val HeaderTile = 56.dp
private val HeaderIcon = 28.dp
private val SkeletonName = 180.dp
private val SkeletonMeta = 120.dp
private val SkeletonButtons = 40.dp
private val SkeletonPanel = 220.dp
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
    shown?.takeIf { it.copy.hasChanges }?.let { unsavedPage(it) { viewModel.on(PageIntent.Discard) } },
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
  val spacing = GlorianaTheme.spacing
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (onWorkbench) spacing.s20 else spacing.s16),
    verticalArrangement = Arrangement.spacedBy(spacing.s8),
  ) {
    if (!onWorkbench) BackButton(stringResource(Res.string.back_to_settings), actions.onBack)
    when (state) {
      ConnectionPageState.Loading, ConnectionPageState.Gone -> Skeleton { PageSkeleton() }
      ConnectionPageState.CouldNotLoad ->
        RetryNote(stringResource(Res.string.connection_error), Res.string.connection_error_detail) {
          actions.onIntent(PageIntent.Retry)
        }
      is ConnectionPageState.Shown -> ShownPage(state, actions, focus)
    }
  }
}

@Composable
private fun ShownPage(page: ConnectionPageState.Shown, actions: ConnectionPageActions, focus: FocusRequesters) {
  val spacing = GlorianaTheme.spacing
  val standard = GlorianaTheme.motion.standard
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s16)) {
    Header(page, actions.onIntent)
    ActionsRow(page, actions.onIntent)
    Column(Modifier.animateContentSize(standard.tween())) {
      AnimatedVisibility(
        page.test != null || page.note != null,
        enter = fadeIn(standard.tween()),
        exit = fadeOut(standard.tween()),
      ) {
        Box(Modifier.padding(bottom = spacing.s8)) { Outcome(page, actions.onIntent) }
      }
    }
    ConnectionPanels(page, FormEditing(page, focus, actions.onIntent))
  }
}

@Composable
private fun Header(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  val saved = page.saved
  val name = page.form.name.ifBlank { saved?.fields?.name.orEmpty() }
  Row(Modifier.padding(top = spacing.s8), horizontalArrangement = Arrangement.spacedBy(spacing.s14)) {
    Box(
      Modifier.size(HeaderTile).background(colors.surface2, GlorianaTheme.shapes.panel),
      contentAlignment = Alignment.Center,
    ) {
      Icon(painterResource(Res.drawable.ic_hub), null, Modifier.size(HeaderIcon), tint = colors.textMuted)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
      if (name.isBlank()) {
        Text(stringResource(Res.string.new_connection), style = type.headlineLarge, color = colors.textSubtle)
      } else {
        Text(name, style = type.headlineLarge, color = colors.text)
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s6),
        verticalArrangement = Arrangement.spacedBy(spacing.s6),
      ) {
        val pieces = saved?.let {
          listOf(
            stringResource(Res.string.followed_by_dot, stringResource(it.fields.format.label)),
            hostOf(it.fields.baseUrl),
          )
        } ?: listOf(stringResource(Res.string.not_saved_yet))
        pieces.forEach { piece ->
          Text(
            piece,
            style = type.bodyMedium,
            color = colors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
        DefaultMark(page.isDefault)
      }
    }
    if (saved != null) {
      ConnectionMoreMenu(
        saved.fields.name,
        page.isDefault,
        onMakeDefault = { onIntent(PageIntent.MakeDefault) },
        onDelete = { onIntent(PageIntent.AskToDelete) },
      )
    }
  }
}

@Composable
private fun ActionsRow(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val spacing = GlorianaTheme.spacing
  val testing = page.activity == PageActivity.Testing
  Row(Modifier.padding(bottom = spacing.s6), horizontalArrangement = Arrangement.spacedBy(spacing.s8)) {
    GlorianaButton(
      stringResource(if (page.activity.isSaving) Res.string.saving else Res.string.save),
      { onIntent(PageIntent.Save) },
      kind = ButtonKind.Primary,
      icon = Res.drawable.ic_check_20,
      enabled = page.canSave,
    )
    GlorianaButton(
      stringResource(if (testing) Res.string.testing else Res.string.test),
      { onIntent(PageIntent.Test) },
      icon = Res.drawable.ic_network_check_20,
      enabled = page.activity == PageActivity.Idle,
      running = testing,
    )
  }
}

@Composable
private fun Outcome(page: ConnectionPageState.Shown, onIntent: (PageIntent) -> Unit) {
  val name = page.form.name.trim().ifEmpty { stringResource(Res.string.this_connection) }
  page.note?.let { note ->
    NoteCard {
      NoteLine(note.text(name))
      if (note == PageNote.NotDeleted) {
        GlorianaButton(
          stringResource(Res.string.retry),
          { onIntent(PageIntent.AskToDelete) },
          icon = Res.drawable.ic_refresh_20,
        )
      }
    }
    return
  }
  when (val test = page.test) {
    is TestOutcome.Passed ->
      PassedBox(
        stringResource(Res.string.test_passed, withOneDecimal(test.took.toDouble(DurationUnit.SECONDS)), test.reply),
      )
    is TestOutcome.Failed -> NoteCard {
      NoteLine(
        boldThenPlain(
          stringResource(Res.string.test_failed, test.failure.reason()),
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
    SkeletonBlock(Modifier.size(width = SkeletonName, height = SkeletonButtons))
    SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanel), on = SkeletonSurface.Raised)
  }
}

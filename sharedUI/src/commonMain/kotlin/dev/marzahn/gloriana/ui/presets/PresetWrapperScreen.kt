package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.ui.components.FieldNote
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.entry_text
import dev.marzahn.gloriana.ui.resources.ic_notes
import dev.marzahn.gloriana.ui.resources.wrapper_hint
import dev.marzahn.gloriana.ui.resources.wrapper_hint_empty
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

private const val MIN_LINES = 5
private val SkeletonLine = 180.dp

@Composable
fun PresetWrapperScreen(route: PresetWrapperRoute, modifier: Modifier = Modifier) {
  val viewModel = rememberPresetPage(route)
  val navigator = LocalShellNavigator.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val page = state as? PresetPageState.Shown
  val back = { navigator.pop(Destination.Library) }
  PresetPageFrame(back, page?.name.orEmpty(), modifier, backOnEveryWidth = true) {
    if (page == null) {
      Skeleton { SkeletonBlock(Modifier.size(width = SkeletonLine, height = GlorianaTheme.spacing.s20)) }
    } else {
      WrapperPage(page, route, viewModel::on, viewModel::contentNow)
    }
  }
}

@Composable
private fun WrapperPage(
  page: PresetPageState.Shown,
  route: PresetWrapperRoute,
  onIntent: (PresetIntent) -> Unit,
  contentNow: () -> PresetContent?,
  modifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type
  val spacing = GlorianaTheme.spacing
  val prompt = route.prompt
  Column(modifier) {
    Column(Modifier.padding(top = spacing.s8), verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
      Text(stringResource(prompt.title), style = type.material.headlineLarge, color = colors.text)
      Text(stringResource(prompt.purpose), style = type.material.bodyMedium, color = colors.textMuted)
    }
    Box(Modifier.padding(top = spacing.s16, bottom = spacing.s8)) { SaveButton(page, onIntent) }
    SaveNote(page)
    WrapperText(page, route, onIntent, contentNow)
  }
}

@Composable
private fun WrapperText(
  page: PresetPageState.Shown,
  route: PresetWrapperRoute,
  onIntent: (PresetIntent) -> Unit,
  contentNow: () -> PresetContent?,
) {
  val type = GlorianaTheme.type
  val spacing = GlorianaTheme.spacing
  val prompt = route.prompt
  val shown = page.content.wrappers[prompt]
  var text by rememberDraftText(route, shown, draftNow = { contentNow()?.wrappers?.get(prompt) })
  Panel(
    Res.drawable.ic_notes,
    stringResource(Res.string.entry_text),
    listOf(
      {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
          GlorianaTextField(
            text,
            {
              text = it
              onIntent(PresetIntent.SetWrapper(prompt, it))
            },
            Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = MIN_LINES,
            textStyle = type.reading,
          )
          val hint = stringResource(Res.string.wrapper_hint)
          FieldNote(if (text.isEmpty()) stringResource(Res.string.wrapper_hint_empty, hint) else hint, null)
        }
      },
    ),
    Modifier.padding(top = spacing.s8),
  )
}

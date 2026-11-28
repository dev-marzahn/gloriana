package dev.marzahn.gloriana.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.ui.components.ButtonSize
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.components.EmptyState
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

private val TitleRowHeight = 64.dp
private val TopBarHeight = 48.dp
private val ListPaneWidth = 330.dp
private val DetailMaxWidth = 700.dp

sealed interface TopLevelContent {
  val idle: EmptyBlock
  val onCreate: (() -> Unit)?
  val reorder: ReorderControl?

  data class Empty(override val idle: EmptyBlock) : TopLevelContent {
    override val onCreate: (() -> Unit)? get() = null
    override val reorder: ReorderControl? get() = null
  }

  class Items(
    override val idle: EmptyBlock,
    override val onCreate: (() -> Unit)? = null,
    override val reorder: ReorderControl? = null,
    val list: @Composable (header: @Composable () -> Unit) -> Unit,
  ) : TopLevelContent
}

private val TopLevelContent.reordering: Boolean get() = reorder?.active == true

private fun Modifier.endsReorderOnEscape(reorder: ReorderControl?): Modifier =
  onKeyEvent { event ->
    event.type == KeyEventType.KeyDown && event.key == Key.Escape && reorder?.endsOnEscape() == true
  }

@Composable
fun TopLevelScreen(
  destination: Destination,
  content: TopLevelContent,
  modifier: Modifier = Modifier,
  kindPicker: (@Composable () -> Unit)? = null,
) {
  val panes = LocalWorkbenchPanes.current
  val screen = modifier.endsReorderOnEscape(content.reorder)
  if (panes != null) {
    Workbench(destination, content, panes, screen, kindPicker)
  } else {
    when (content) {
      is TopLevelContent.Empty -> Column(screen.fillMaxSize()) {
        TitleRow(destination, content)
        kindPicker?.let { KindPickerRow(it) }
        EmptyState(content.idle, Modifier.weight(1f).fillMaxWidth().padding(GlorianaTheme.spacing.s16))
      }
      is TopLevelContent.Items -> Box(screen.fillMaxSize()) {
        content.list {
          Column {
            TitleRow(destination, content)
            kindPicker?.let { KindPickerRow(it) }
            if (content.reordering) ReorderHint(withKeys = false)
          }
        }
      }
    }
  }
}

@Composable
private fun KindPickerRow(kindPicker: @Composable () -> Unit) {
  Box(Modifier.fillMaxWidth().padding(bottom = GlorianaTheme.spacing.s8)) { kindPicker() }
}

@Composable
private fun Workbench(
  destination: Destination,
  content: TopLevelContent,
  panes: WorkbenchPanes,
  modifier: Modifier = Modifier,
  kindPicker: (@Composable () -> Unit)? = null,
) {
  val colors = GlorianaTheme.colors
  Column(modifier.fillMaxSize()) {
    TopBar(destination, content)
    Row(Modifier.weight(1f).fillMaxWidth()) {
      Column(Modifier.width(ListPaneWidth).fillMaxHeight().background(colors.pane)) {
        kindPicker?.let { Box(Modifier.fillMaxWidth().padding(GlorianaTheme.spacing.s8)) { it() } }
        if (content.reordering) ReorderHint(withKeys = true)
        if (content is TopLevelContent.Items) Box(Modifier.weight(1f)) { content.list {} }
      }
      VerticalDivider(color = colors.border)
      DetailPane(panes.detail, content.idle, Modifier.weight(1f).fillMaxHeight())
    }
  }
}

@Composable
private fun DetailPane(detail: NavEntry<NavKey>?, idle: EmptyBlock, modifier: Modifier = Modifier) {
  val motion = GlorianaTheme.motion
  AnimatedContent(
    targetState = detail,
    modifier = modifier.background(GlorianaTheme.colors.canvas),
    transitionSpec = { fadeIn(motion.paneIn.tween()) togetherWith fadeOut(motion.paneOut.tween()) },
    contentAlignment = Alignment.TopCenter,
    contentKey = { it?.contentKey },
  ) { shown ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Box(Modifier.widthIn(max = DetailMaxWidth).fillMaxSize()) {
        if (shown != null) {
          shown.Content()
        } else {
          EmptyState(idle, Modifier.fillMaxSize().padding(GlorianaTheme.spacing.s20))
        }
      }
    }
  }
}

@Composable
private fun TopBar(destination: Destination, content: TopLevelContent) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Column(Modifier.fillMaxWidth().background(colors.canvas)) {
    Row(
      Modifier.fillMaxWidth().heightIn(min = TopBarHeight).padding(start = spacing.s20, end = spacing.s12),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(destination.label),
        Modifier.weight(1f),
        style = GlorianaTheme.type.material.titleLarge,
        color = colors.text,
      )
      TitleActions(content.onCreate, content.reorder, ButtonSize.Small)
    }
    HorizontalDivider(color = colors.border)
  }
}

@Composable
private fun TitleRow(destination: Destination, content: TopLevelContent) {
  val spacing = GlorianaTheme.spacing
  Row(
    Modifier.fillMaxWidth().heightIn(min = TitleRowHeight).padding(horizontal = spacing.s16),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(destination.label),
      Modifier.weight(1f),
      style = GlorianaTheme.type.material.headlineLarge,
      color = GlorianaTheme.colors.text,
    )
    TitleActions(content.onCreate, content.reorder, ButtonSize.Regular)
  }
}

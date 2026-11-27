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
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.ui.components.ButtonSize
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.components.EmptyState
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.ic_add_20
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

private val TitleRowHeight = 64.dp
private val TopBarHeight = 48.dp
private val ListPaneWidth = 330.dp
private val DetailMaxWidth = 700.dp

sealed interface TopLevelContent {
  data class Empty(val block: EmptyBlock) : TopLevelContent

  class Items(
    val nothingPicked: EmptyBlock,
    val onCreate: (() -> Unit)? = null,
    val list: @Composable (header: @Composable () -> Unit) -> Unit,
  ) : TopLevelContent
}

private val TopLevelContent.createAction: (() -> Unit)?
  get() = when (this) {
    is TopLevelContent.Empty -> null
    is TopLevelContent.Items -> onCreate
  }

@Composable
fun TopLevelScreen(destination: Destination, content: TopLevelContent, modifier: Modifier = Modifier) {
  val panes = LocalWorkbenchPanes.current
  if (panes != null) {
    Workbench(destination, content, panes, modifier)
  } else {
    when (content) {
      is TopLevelContent.Empty -> Column(modifier.fillMaxSize()) {
        TitleRow(destination, onCreate = null)
        EmptyState(content.block, Modifier.weight(1f).fillMaxWidth().padding(GlorianaTheme.spacing.s16))
      }
      is TopLevelContent.Items -> Box(modifier.fillMaxSize()) {
        content.list { TitleRow(destination, content.onCreate) }
      }
    }
  }
}

@Composable
private fun Workbench(
  destination: Destination,
  content: TopLevelContent,
  panes: WorkbenchPanes,
  modifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val idle = when (content) {
    is TopLevelContent.Empty -> content.block
    is TopLevelContent.Items -> content.nothingPicked
  }
  Column(modifier.fillMaxSize()) {
    TopBar(destination, content.createAction)
    Row(Modifier.weight(1f).fillMaxWidth()) {
      Box(Modifier.width(ListPaneWidth).fillMaxHeight().background(colors.pane)) {
        if (content is TopLevelContent.Items) content.list {}
      }
      VerticalDivider(color = colors.border)
      DetailPane(panes.detail, idle, Modifier.weight(1f).fillMaxHeight())
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
private fun TopBar(destination: Destination, onCreate: (() -> Unit)?) {
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
      if (onCreate != null) CreateButton(onCreate, ButtonSize.Small)
    }
    HorizontalDivider(color = colors.border)
  }
}

@Composable
private fun TitleRow(destination: Destination, onCreate: (() -> Unit)?) {
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
    if (onCreate != null) CreateButton(onCreate, ButtonSize.Regular)
  }
}

@Composable
private fun CreateButton(onCreate: () -> Unit, size: ButtonSize) {
  GlorianaButton(
    stringResource(Res.string.create),
    onCreate,
    icon = Res.drawable.ic_add_20,
    size = size,
  )
}

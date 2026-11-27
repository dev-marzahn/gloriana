package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

@Immutable
internal class WorkbenchPanes(val detail: NavEntry<NavKey>?)

internal val LocalWorkbenchPanes = staticCompositionLocalOf<WorkbenchPanes?> { null }

internal object ListDetailSceneStrategy : SceneStrategy<NavKey> {
  override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey> =
    ListDetailScene(entries)
}

private class ListDetailScene(override val entries: List<NavEntry<NavKey>>) : Scene<NavKey> {
  private val list = entries.first()
  private val panes = WorkbenchPanes(detail = entries.drop(1).lastOrNull())

  override val key: Any = list.contentKey
  override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)
  override val content: @Composable () -> Unit = {
    CompositionLocalProvider(LocalWorkbenchPanes provides panes) { list.Content() }
  }
}

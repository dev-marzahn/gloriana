package dev.marzahn.gloriana.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.Shell
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.platformHasSystemBack
import dev.marzahn.gloriana.ui.navigation.rememberShellNavigator
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun App(modifier: Modifier = Modifier) {
  GlorianaApp(koinEntryProvider(), modifier)
}

@Composable
internal fun GlorianaApp(
  entryProvider: (NavKey) -> NavEntry<NavKey>,
  modifier: Modifier = Modifier,
  navigator: ShellNavigator = rememberShellNavigator(),
  hasSystemBack: Boolean = platformHasSystemBack,
) {
  val decorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
    rememberViewModelStoreNavEntryDecorator(),
  )
  val entries = Destination.entries.associateWith { destination ->
    rememberDecoratedNavEntries(navigator.stack(destination), decorators, entryProvider)
  }
  BoxWithConstraints(modifier) {
    GlorianaTheme(WidthClass.of(maxWidth)) {
      Shell(navigator, entries, hasSystemBack)
    }
  }
}

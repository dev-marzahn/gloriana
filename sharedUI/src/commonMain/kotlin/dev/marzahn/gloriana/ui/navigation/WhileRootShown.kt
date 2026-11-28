package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope

@Composable
fun WhileRootShown(destination: Destination, vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
  val navigator = LocalShellNavigator.current
  val onWorkbench = LocalWorkbenchPanes.current != null
  val shown by remember(navigator, destination, onWorkbench) {
    derivedStateOf { navigator.current == destination && (onWorkbench || navigator.isAtRoot(destination)) }
  }
  if (shown) LaunchedEffect(navigator, *keys, block = block)
}

@Composable
fun <P> OpenPagesWhileShown(
  destination: Destination,
  openPagesWhileShown: suspend (open: (P) -> Unit) -> Nothing,
  open: (P) -> Unit,
) {
  val currentOpen by rememberUpdatedState(open)
  WhileRootShown(destination, openPagesWhileShown) { openPagesWhileShown { currentOpen(it) } }
}

package dev.marzahn.gloriana.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.theme.GlorianaTheme

@Composable
fun GlorianaSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  SnackbarHost(hostState, modifier) { data ->
    Snackbar(
      data,
      shape = GlorianaTheme.shapes.control,
      containerColor = colors.inverse,
      contentColor = colors.inverseFg,
      actionColor = colors.inverseAccent,
    )
  }
}

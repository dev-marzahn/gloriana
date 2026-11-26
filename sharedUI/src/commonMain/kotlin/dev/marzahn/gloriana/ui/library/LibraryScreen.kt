package dev.marzahn.gloriana.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import dev.marzahn.gloriana.ui.resources.library_empty
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LibraryContent(state, modifier)
}

@Composable
internal fun LibraryContent(state: LibraryState, modifier: Modifier = Modifier) {
  Scaffold(modifier) { insets ->
    Column(Modifier.padding(insets)) {
      Text(stringResource(Res.string.app_name))
      when (state) {
        LibraryState.Empty -> Text(stringResource(Res.string.library_empty))
      }
    }
  }
}

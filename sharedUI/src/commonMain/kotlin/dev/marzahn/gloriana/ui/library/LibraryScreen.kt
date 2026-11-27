package dev.marzahn.gloriana.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_person_add
import dev.marzahn.gloriana.ui.resources.library_empty
import dev.marzahn.gloriana.ui.resources.library_empty_text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LibraryContent(state, modifier)
}

@Composable
internal fun LibraryContent(state: LibraryState, modifier: Modifier = Modifier) {
  when (state) {
    LibraryState.Empty -> {
      val empty = EmptyBlock(
        icon = Res.drawable.ic_person_add,
        heading = stringResource(Res.string.library_empty),
        text = stringResource(Res.string.library_empty_text),
      )
      TopLevelScreen(Destination.Library, TopLevelContent.Empty(empty), modifier)
    }
  }
}

package dev.marzahn.gloriana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.not_made_default
import dev.marzahn.gloriana.ui.resources.not_made_default_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class NotDeleted { Failed, IsDefault }

class RefusedAsDefaultCopy(val headline: StringResource, val detail: StringResource)

@Composable
fun NotDeletedNote(
  why: NotDeleted,
  name: String,
  refused: RefusedAsDefaultCopy,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (why) {
    NotDeleted.Failed -> NotDeletedNote(name, onRetry, modifier)
    NotDeleted.IsDefault -> NoteCard(modifier) {
      NoteLine(boldThenPlain(stringResource(refused.headline, name), stringResource(refused.detail)))
    }
  }
}

@Composable
fun NotMadeDefaultNote(name: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  RetryNote(stringResource(Res.string.not_made_default, name), Res.string.not_made_default_detail, modifier, onRetry)
}

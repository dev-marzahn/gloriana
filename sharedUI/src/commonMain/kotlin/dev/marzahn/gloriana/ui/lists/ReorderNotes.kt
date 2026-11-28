package dev.marzahn.gloriana.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.reorder_lifted
import dev.marzahn.gloriana.ui.resources.reorder_moved
import dev.marzahn.gloriana.ui.resources.reorder_not_moved
import dev.marzahn.gloriana.ui.resources.reorder_not_moved_detail
import dev.marzahn.gloriana.ui.resources.reorder_not_moved_storage
import dev.marzahn.gloriana.ui.resources.reorder_not_moved_storage_detail
import dev.marzahn.gloriana.ui.resources.reorder_step
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun ReorderAnnouncement(rows: RowOrder, saved: SavedMove?, modifier: Modifier = Modifier) {
  val shown = rows.shown
  val place: (Uuid) -> Pair<Int, Int> = { id -> shown.indexOf(id) + 1 to shown.size }
  val spoken = rows.lift.spoken
  val spokenText = spoken?.let { said ->
    val (at, of) = place(said.id)
    when (said) {
      is Spoken.Lifted -> stringResource(Res.string.reorder_lifted, rows.name(said.id), at, of)
      is Spoken.Stepped -> stringResource(Res.string.reorder_step, rows.name(said.id), at, of)
    }
  }
  val savedText = saved?.let { stringResource(Res.string.reorder_moved, it.name, place(it.id).first, shown.size) }
  val savedBefore = remember { saved }
  var text by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(spoken) { spokenText?.let { text = it } }
  LaunchedEffect(saved) { if (saved != savedBefore) savedText?.let { text = it } }
  text?.let { said ->
    Box(
      modifier.size(0.dp).semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = said
      },
    )
  }
}

@Composable
fun NotMovedNote(notMoved: NotMoved, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val (bold, detail) = if (notMoved.storageFull) {
    Res.string.reorder_not_moved_storage to Res.string.reorder_not_moved_storage_detail
  } else {
    Res.string.reorder_not_moved to Res.string.reorder_not_moved_detail
  }
  NoteCard(modifier, onDismiss = onDismiss) {
    NoteLine(boldThenPlain(stringResource(bold, notMoved.name), stringResource(detail)))
  }
}

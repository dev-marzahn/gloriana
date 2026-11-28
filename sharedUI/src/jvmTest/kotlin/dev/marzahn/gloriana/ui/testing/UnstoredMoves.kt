package dev.marzahn.gloriana.ui.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.marzahn.gloriana.logic.storage.MoveResult
import dev.marzahn.gloriana.logic.storage.Reordering
import dev.marzahn.gloriana.ui.lists.UserOrderMoves
import kotlinx.coroutines.flow.emptyFlow
import kotlin.uuid.Uuid

private val NothingStored = Reordering { _, _ -> MoveResult.Moved }

@Composable
fun rememberUnstoredMoves(): UserOrderMoves<*> {
  val scope = rememberCoroutineScope()
  return remember(scope) { UserOrderMoves(scope, NothingStored, emptyFlow<List<Uuid>>()) { it } }
}

package dev.marzahn.gloriana.ui.lists

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

class RowFocus(val key: String, val listIndex: Int)

@Composable
fun rememberRowFocus(
  listState: LazyListState,
  target: RowFocus?,
  onFocus: () -> Unit,
): (String) -> Modifier {
  val requester = remember { FocusRequester() }
  val currentOnFocus by rememberUpdatedState(onFocus)
  LaunchedEffect(target?.key) {
    val focused = target ?: return@LaunchedEffect
    listState.scrollToItem(focused.listIndex)
    requester.requestFocus()
    currentOnFocus()
  }
  return { key -> if (key == target?.key) Modifier.focusRequester(requester) else Modifier }
}

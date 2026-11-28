package dev.marzahn.gloriana.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun rememberRowFocus(list: ReorderableList, target: String?, onFocus: () -> Unit): (String) -> Modifier {
  val requester = remember { FocusRequester() }
  val currentOnFocus by rememberUpdatedState(onFocus)
  LaunchedEffect(target) {
    val key = target ?: return@LaunchedEffect
    list.indexOf(key).takeIf { it >= 0 }?.let { list.listState.scrollToItem(it) }
    requester.requestFocus()
    currentOnFocus()
  }
  return { key -> if (key == target) Modifier.focusRequester(requester) else Modifier }
}

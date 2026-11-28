package dev.marzahn.gloriana.ui.presets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey

@Composable
internal fun rememberDraftText(
  route: NavKey,
  shown: String,
  draftNow: () -> String?,
  sameValue: (String, String) -> Boolean = String::equals,
): MutableState<String> {
  val field = remember(route) { mutableStateOf(shown) }
  val currentDraft by rememberUpdatedState(draftNow)
  val currentSameValue by rememberUpdatedState(sameValue)
  LaunchedEffect(route, shown) {
    val target = currentDraft() ?: shown
    if (!currentSameValue(field.value, target)) field.value = target
  }
  return field
}

package dev.marzahn.gloriana.ui.characters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

@Composable
fun FocusFirstForKeyboard(requester: FocusRequester) {
  val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
  LaunchedEffect(keyboard) { if (keyboard) requester.requestFocus() }
}

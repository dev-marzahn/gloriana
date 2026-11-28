package dev.marzahn.gloriana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal actual fun dialogProperties(scrim: Scrim, dismissible: Boolean): DialogProperties = DialogProperties(
  dismissOnBackPress = dismissible,
  dismissOnClickOutside = dismissible,
  usePlatformDefaultWidth = false,
)

@Composable
internal actual fun ApplyDialogScrim(scrim: Scrim) {
  val window = (LocalView.current.parent as DialogWindowProvider).window
  SideEffect { window.setDimAmount(scrim.alpha) }
}

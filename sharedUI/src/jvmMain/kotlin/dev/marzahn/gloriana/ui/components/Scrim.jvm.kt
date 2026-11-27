package dev.marzahn.gloriana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

internal actual fun dialogProperties(scrim: Scrim): DialogProperties =
  DialogProperties(usePlatformDefaultWidth = false, scrimColor = scrim.tinted)

@Composable
internal actual fun ApplyDialogScrim(scrim: Scrim) = Unit

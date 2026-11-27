package dev.marzahn.gloriana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

@Immutable
internal class Scrim(val color: Color, val alpha: Float) {
  val tinted: Color get() = color.copy(alpha = alpha)
}

internal expect fun dialogProperties(scrim: Scrim, dismissible: Boolean): DialogProperties

@Composable
internal expect fun ApplyDialogScrim(scrim: Scrim)

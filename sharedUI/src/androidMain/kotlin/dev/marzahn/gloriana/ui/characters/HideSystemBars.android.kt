package dev.marzahn.gloriana.ui.characters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal actual fun HideSystemBars() {
  val view = LocalView.current
  DisposableEffect(view) {
    val window = (view.parent as? DialogWindowProvider)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller?.hide(WindowInsetsCompat.Type.systemBars())
    onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
  }
}

package dev.marzahn.gloriana.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.marzahn.gloriana.desktop.di.desktopAppModule
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import java.awt.Dimension

private val OpeningSize = DpSize(1280.dp, 800.dp)
private val MinimumSize = DpSize(400.dp, 600.dp)

private fun DpSize.inAwtUnits() = Dimension(width.value.toInt(), height.value.toInt())

fun main() {
  startKoin { modules(desktopAppModule) }
  application {
    Window(
      onCloseRequest = ::exitApplication,
      state = rememberWindowState(size = OpeningSize),
      title = stringResource(Res.string.app_name),
    ) {
      LaunchedEffect(window) { window.minimumSize = MinimumSize.inAwtUnits() }
      App()
    }
  }
}

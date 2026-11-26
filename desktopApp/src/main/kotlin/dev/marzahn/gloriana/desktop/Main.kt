package dev.marzahn.gloriana.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.marzahn.gloriana.desktop.di.desktopAppModule
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin

fun main() {
  startKoin { modules(desktopAppModule) }
  application {
    Window(onCloseRequest = ::exitApplication, title = stringResource(Res.string.app_name)) {
      App()
    }
  }
}

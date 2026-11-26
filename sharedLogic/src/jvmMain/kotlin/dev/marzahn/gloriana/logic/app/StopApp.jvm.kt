package dev.marzahn.gloriana.logic.app

import kotlin.system.exitProcess

actual fun stopApp(cause: Throwable) {
  cause.printStackTrace()
  exitProcess(1)
}

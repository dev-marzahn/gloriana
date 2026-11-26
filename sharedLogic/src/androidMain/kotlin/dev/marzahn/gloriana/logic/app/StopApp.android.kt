package dev.marzahn.gloriana.logic.app

actual fun stopApp(cause: Throwable) {
  checkNotNull(Thread.getDefaultUncaughtExceptionHandler()).uncaughtException(Thread.currentThread(), cause)
}

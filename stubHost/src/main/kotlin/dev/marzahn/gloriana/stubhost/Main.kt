package dev.marzahn.gloriana.stubhost

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  when (val launch = Launch.from(args.toList())) {
    Launch.ShowHelp -> println(LaunchOptions.help)
    is Launch.Exit -> {
      System.err.println(launch.message)
      exitProcess(launch.reason.exitCode)
    }
    is Launch.Start -> serve(launch)
  }
}

private fun serve(launch: Launch.Start): Unit = runBlocking {
  val endpoints = Endpoints(Modes(launch.options.delay, launch.options.slowPause))
  val host = startStubHost(launch.record, launch.options.port, endpoints)
  println("Stub host on ${host.address}")
  println("Recording requests to ${launch.record.file.toAbsolutePath()} (also at ${host.address}$RECORD_PATH)")
  println(endpoints.howToCall)
  awaitCancellation()
}

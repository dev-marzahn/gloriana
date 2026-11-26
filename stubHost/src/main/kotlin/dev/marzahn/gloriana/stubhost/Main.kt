package dev.marzahn.gloriana.stubhost

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val LOOPBACK = "127.0.0.1"

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
  val server = embeddedServer(CIO, port = launch.options.port, host = LOOPBACK) {
    stubHost(launch.record, endpoints)
  }.startSuspend(wait = false)
  val address = "http://$LOOPBACK:${server.engine.resolvedConnectors().first().port}"
  println("Stub host on $address")
  println("Recording requests to ${launch.record.file.toAbsolutePath()} (also at $address$RECORD_PATH)")
  println(endpoints.howToCall)
  awaitCancellation()
}

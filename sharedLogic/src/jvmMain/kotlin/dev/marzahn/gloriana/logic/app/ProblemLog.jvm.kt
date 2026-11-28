package dev.marzahn.gloriana.logic.app

actual val platformProblemLog = ProblemLog { message, cause ->
  System.err.println(message)
  cause.printStackTrace()
}

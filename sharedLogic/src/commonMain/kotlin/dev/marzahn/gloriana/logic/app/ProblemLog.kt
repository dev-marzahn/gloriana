package dev.marzahn.gloriana.logic.app

fun interface ProblemLog {
  fun record(message: String, cause: Throwable)
}

expect val platformProblemLog: ProblemLog

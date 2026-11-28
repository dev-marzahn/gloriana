package dev.marzahn.gloriana.logic.app

import android.util.Log

actual val platformProblemLog = ProblemLog { message, cause -> Log.e("Gloriana", message, cause) }

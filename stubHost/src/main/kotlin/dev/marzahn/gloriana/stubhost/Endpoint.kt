package dev.marzahn.gloriana.stubhost

import io.ktor.http.HttpMethod

internal sealed interface Target {
  data class Model(val mode: Mode, val format: ApiFormat) : Target

  data class NoModel(val format: ApiFormat) : Target

  data class ModelList(val mode: Mode) : Target

  data object Nowhere : Target
}

internal class Endpoints(private val modes: Modes) {
  val howToCall: String =
    "Send POST [/<label>...]/<mode>$VERSION{${ApiFormat.all.joinToString("|") { it.path }}}, " +
      "or GET [/<label>...]/<mode>$VERSION$MODELS_PATH, " +
      "where <mode> is one of ${modes.names.joinToString(", ")}."

  fun resolve(method: HttpMethod, path: String): Target {
    val versionAt = path.lastIndexOf(VERSION)
    if (versionAt < 0) return Target.Nowhere
    val formatPath = path.substring(versionAt + VERSION.length)
    val base = path.substring(0, versionAt)
    val mode = if (base.startsWith("/")) modes.named(base.substringAfterLast('/')) else null
    val format = ApiFormat.byPath[formatPath]
    return when {
      formatPath == MODELS_PATH && mode != null && method == HttpMethod.Get -> Target.ModelList(mode)
      format == null -> Target.Nowhere
      mode != null && method == HttpMethod.Post -> Target.Model(mode, format)
      else -> Target.NoModel(format)
    }
  }

  private companion object {
    const val VERSION = "/v1/"
  }
}

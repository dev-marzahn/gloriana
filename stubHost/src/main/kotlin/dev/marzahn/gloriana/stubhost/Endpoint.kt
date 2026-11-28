package dev.marzahn.gloriana.stubhost

import io.ktor.http.HttpMethod

private const val VERSION = "/v1/"

internal data class PathAroundMode(val beforeMode: String, val formatPath: String) {
  fun withMode(name: String): String = "$beforeMode/$name$VERSION$formatPath"
}

internal sealed interface Target {
  data class Model(val mode: Mode, val format: ApiFormat, val path: PathAroundMode) : Target

  data class NoModel(val format: ApiFormat) : Target

  data class ModelList(val mode: Mode, val path: PathAroundMode) : Target

  data object Nowhere : Target
}

internal class Endpoints(private val modes: Modes) {
  val howToCall: String =
    "Send POST [/<label>...]/<mode>$VERSION{${ApiFormat.all.joinToString("|") { it.path }}}, " +
      "or GET [/<label>...]/<mode>$VERSION$MODELS_PATH, " +
      "where <mode> is one of ${modes.names.joinToString(", ")}."

  fun resolve(method: HttpMethod, path: String): Target {
    if (VERSION !in path) return Target.Nowhere
    val base = path.substringBeforeLast(VERSION)
    val formatPath = path.substringAfterLast(VERSION)
    val mode = if (base.startsWith("/")) modes.named(base.substringAfterLast('/')) else null
    val around = PathAroundMode(base.substringBeforeLast('/'), formatPath)
    val format = ApiFormat.byPath[formatPath]
    return when {
      formatPath == MODELS_PATH && mode != null && method == HttpMethod.Get -> Target.ModelList(mode, around)
      format == null -> Target.Nowhere
      mode != null && method == HttpMethod.Post -> Target.Model(mode, format, around)
      else -> Target.NoModel(format)
    }
  }
}

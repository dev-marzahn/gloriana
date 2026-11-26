package dev.marzahn.gloriana.stubhost

import io.ktor.http.HttpMethod

internal sealed interface Target {
  data class Model(val mode: Mode, val format: ApiFormat) : Target

  data class NoModel(val format: ApiFormat) : Target

  data object Nowhere : Target
}

internal class Endpoints(private val modes: Modes) {
  val howToCall: String =
    "Send POST [/<label>...]/<mode>$VERSION{${ApiFormat.all.joinToString("|") { it.path }}}, " +
      "where <mode> is one of ${modes.names.joinToString(", ")}."

  fun resolve(method: HttpMethod, path: String): Target {
    val versionAt = path.lastIndexOf(VERSION)
    if (versionAt < 0) return Target.Nowhere
    val format = ApiFormat.byPath[path.substring(versionAt + VERSION.length)]
    val base = path.substring(0, versionAt)
    val mode = if (base.startsWith("/")) modes.named(base.substringAfterLast('/')) else null
    val posted = method == HttpMethod.Post
    return when {
      format == null -> Target.Nowhere
      mode != null && posted -> Target.Model(mode, format)
      else -> Target.NoModel(format)
    }
  }

  private companion object {
    const val VERSION = "/v1/"
  }
}

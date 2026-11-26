package dev.marzahn.gloriana.logic.storage

import java.nio.file.Path
import kotlin.io.path.Path

private const val DEBUGGABLE_PROPERTY = "gloriana.debuggable"

enum class DesktopBuild(val directoryName: String) {
  Release("Gloriana"),
  Debuggable("Gloriana Debug"),
  ;

  companion object {
    fun current() = if (System.getProperty(DEBUGGABLE_PROPERTY).toBoolean()) Debuggable else Release
  }
}

class DesktopEnvironment(
  private val osName: String,
  private val userHome: String,
  private val build: DesktopBuild,
  private val environmentVariable: (String) -> String?,
) {
  val dataDirectory: Path
    get() = applicationDataRoot().resolve(build.directoryName)

  private fun applicationDataRoot(): Path {
    val os = osName.lowercase()
    val home = Path(userHome)
    return when {
      os.startsWith("mac") -> home.resolve("Library").resolve("Application Support")
      os.startsWith("windows") -> pathVariable("APPDATA") { WINDOWS_ABSOLUTE.matches(it) }
        ?: home.resolve("AppData").resolve("Roaming")
      else -> pathVariable("XDG_DATA_HOME") { it.startsWith('/') } ?: home.resolve(".local").resolve("share")
    }
  }

  private fun pathVariable(name: String, isAbsolute: (String) -> Boolean): Path? =
    environmentVariable(name)?.takeIf(isAbsolute)?.let(::Path)

  companion object {
    private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:[\\\\/].*")

    fun current() = DesktopEnvironment(
      osName = System.getProperty("os.name"),
      userHome = System.getProperty("user.home"),
      build = DesktopBuild.current(),
      environmentVariable = System::getenv,
    )
  }
}

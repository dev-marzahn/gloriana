package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.DebugPurge
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import kotlinx.coroutines.delay
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.time.Duration.Companion.milliseconds

private const val HOOKS_DIRECTORY = "debug"
private const val HOOKS_FILE = "hooks.properties"
private const val RELEASE_FILE = "release-import"
private const val FAULT_PREFIX = "fault."
private const val HOLD_KEY = "import.hold"
private const val PURGE_KEY = "purge.now"
private const val ON = "on"
private const val KILLED_BY_FAULT_POINT = 1
private val RELEASE_POLL = 200.milliseconds

fun interface DebuggableBuild {
  fun isDebuggable(): Boolean
}

enum class FaultAction(val key: String) {
  Fail("fail"),
  Kill("kill"),
}

class FaultInjected(point: CharacterWritePoint) : RuntimeException("fault point $point")

internal class DebugHooks(library: LibraryDirectory, build: DebuggableBuild) {
  private val directory: Path? = library.path().resolve(HOOKS_DIRECTORY).takeIf { build.isDebuggable() }

  val faults = CharacterWriteFaults { point ->
    when (setting(FAULT_PREFIX + point.name)?.let { value -> FaultAction.entries.firstOrNull { it.key == value } }) {
      FaultAction.Fail -> throw FaultInjected(point)
      FaultAction.Kill -> Runtime.getRuntime().halt(KILLED_BY_FAULT_POINT)
      null -> Unit
    }
  }

  val importHold = ImportHold {
    val release = directory?.resolve(RELEASE_FILE)
    while (release != null && setting(HOLD_KEY) == ON && !Files.deleteIfExists(release)) delay(RELEASE_POLL)
  }

  val purge = DebugPurge { setting(PURGE_KEY) == ON }

  private fun setting(key: String): String? {
    val file = directory?.resolve(HOOKS_FILE)?.takeIf(Files::isRegularFile) ?: return null
    return Properties().apply { Files.newInputStream(file).use(::load) }.getProperty(key)?.trim()
  }
}

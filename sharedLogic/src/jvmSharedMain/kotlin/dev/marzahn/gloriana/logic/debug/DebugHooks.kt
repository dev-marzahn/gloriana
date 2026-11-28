package dev.marzahn.gloriana.logic.debug

import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private const val HOOKS_DIRECTORY = "debug"
private const val HOOKS_FILE = "hooks.properties"
private const val FAULT_PREFIX = "fault."
private const val ON = "on"
private const val KILLED_BY_FAULT_POINT = 1

fun interface DebuggableBuild {
  fun isDebuggable(): Boolean
}

enum class FaultAction(val key: String) {
  Fail("fail"),
  Kill("kill"),
}

class DebugHooks(library: LibraryDirectory, build: DebuggableBuild) : Faults, DebugSwitches {
  private val directory: Path? = library.path().resolve(HOOKS_DIRECTORY).takeIf { build.isDebuggable() }

  override fun reached(point: FaultPoint) {
    when (setting(FAULT_PREFIX + point.key)?.let { value -> FaultAction.entries.firstOrNull { it.key == value } }) {
      FaultAction.Fail -> throw FaultInjected(point)
      FaultAction.Kill -> Runtime.getRuntime().halt(KILLED_BY_FAULT_POINT)
      null -> Unit
    }
  }

  override fun isOn(switch: DebugSwitch): Boolean = setting(switch.key) == ON

  override fun take(signal: DebugSignal): Boolean =
    directory?.resolve(signal.fileName)?.let(Files::deleteIfExists) ?: false

  private fun setting(key: String): String? {
    val file = directory?.resolve(HOOKS_FILE)?.takeIf(Files::isRegularFile) ?: return null
    return Properties().apply { Files.newInputStream(file).use(::load) }.getProperty(key)?.trim()
  }
}

internal actual val debugModule: Module = module {
  single { DebugHooks(get(), get()) } binds arrayOf(Faults::class, DebugSwitches::class)
}

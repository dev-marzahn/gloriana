package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.debug.DebugSignal
import dev.marzahn.gloriana.logic.debug.DebugSwitch
import dev.marzahn.gloriana.logic.debug.DebugSwitches
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val RELEASE_POLL = 200.milliseconds

enum class CharacterSwitch(override val key: String) : DebugSwitch {
  ImportHold("characters.import.hold"),
}

object ImportRelease : DebugSignal {
  override val fileName = "release-import"
}

internal class DebugImportHold(private val switches: DebugSwitches) : ImportHold {
  override suspend fun awaitRelease(fileName: String) {
    while (switches.isOn(CharacterSwitch.ImportHold) && !switches.take(ImportRelease)) delay(RELEASE_POLL)
  }
}

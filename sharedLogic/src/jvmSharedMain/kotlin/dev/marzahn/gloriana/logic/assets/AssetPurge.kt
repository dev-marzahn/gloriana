package dev.marzahn.gloriana.logic.assets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.debug.DebugSwitch
import dev.marzahn.gloriana.logic.debug.DebugSwitches
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import kotlinx.coroutines.launch
import java.nio.file.Files
import kotlin.time.Duration.Companion.hours

private val UNUSED_FOR = 24.hours
private val PURGE_EVERY = 24.hours
private const val LAST_PURGE_FILE = "last-purge"
private val KEPT_COPIES = listOf("pre-migration", "pre-restore.db")

internal enum class PurgeRule { Scheduled, Now }

internal class AssetPurge(
  private val store: AssetStore,
  private val library: LibraryDirectory,
  private val references: AssetReferences,
  private val lock: AssetWriteLock,
  private val clock: UpdatedAtClock,
) {
  suspend fun run(rule: PurgeRule): Int? = lock.unlessImporting {
    if (KEPT_COPIES.any { Files.exists(library.path().resolve(it)) }) return@unlessImporting 0
    val now = clock.now()
    val cutoff = if (rule == PurgeRule.Now) Long.MAX_VALUE else now - UNUSED_FOR.inWholeMilliseconds
    val referenced = references.referencedHashes()
    store.stagingFiles().filter { it.modifiedAt < now - UNUSED_FOR.inWholeMilliseconds }
      .forEach { storeIo { Files.deleteIfExists(it.file) } }
    val unused = store.storedFiles().filter { it.hash !in referenced && it.modifiedAt <= cutoff }
    unused.forEach { store.delete(it.hash) }
    storeIo { Files.write(library.path().resolve(LAST_PURGE_FILE), now.toString().toByteArray()) }
    unused.size
  }

  fun isDue(): Boolean {
    val last = library.path().resolve(LAST_PURGE_FILE)
    val lastRun = runCatching { String(Files.readAllBytes(last)).trim().toLong() }.getOrNull()
    return lastRun == null || clock.now() - lastRun >= PURGE_EVERY.inWholeMilliseconds
  }
}

enum class AssetSwitch(override val key: String) : DebugSwitch {
  PurgeNow("assets.purge.now"),
}

internal class AssetPurgeAtStart(purge: AssetPurge, switches: DebugSwitches, scope: ApplicationScope) {
  init {
    purgeRuleAtStart(switches, purge::isDue)?.let { rule -> scope.launch { runCatching { purge.run(rule) } } }
  }
}

internal fun purgeRuleAtStart(switches: DebugSwitches, isDue: () -> Boolean): PurgeRule? = when {
  switches.isOn(AssetSwitch.PurgeNow) -> PurgeRule.Now
  isDue() -> PurgeRule.Scheduled
  else -> null
}

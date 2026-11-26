package dev.marzahn.gloriana.logic.storage

import kotlin.time.Clock

class UpdatedAtClock(private val clock: Clock = Clock.System) {
  fun now(): Long = clock.now().toEpochMilliseconds()

  fun updatedAt(previous: Long?, now: Long = now()): Long = if (previous == null) now else maxOf(now, previous + 1)
}

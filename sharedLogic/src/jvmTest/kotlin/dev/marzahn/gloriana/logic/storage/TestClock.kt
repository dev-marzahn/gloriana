package dev.marzahn.gloriana.logic.storage

import kotlin.time.Clock
import kotlin.time.Instant

class TestClock(var epochMillis: Long) : Clock {
  override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
}

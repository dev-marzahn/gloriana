package dev.marzahn.gloriana.logic.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdatedAtClockTest {
  private val clock = TestClock(epochMillis = 1_000)
  private val stamps = UpdatedAtClock(clock)

  @Test
  fun aNewRowIsStampedNow() {
    assertEquals(1_000, stamps.updatedAt(previous = null))
  }

  @Test
  fun aRowWrittenEarlierIsStampedNow() {
    assertEquals(1_000, stamps.updatedAt(previous = 400))
  }

  @Test
  fun aClockThatSteppedBackStillMovesTheRowForward() {
    clock.epochMillis = 500

    assertEquals(1_001, stamps.updatedAt(previous = 1_000))
  }

  @Test
  fun twoWritesInTheSameMillisecondStillGrow() {
    assertEquals(1_001, stamps.updatedAt(previous = 1_000))
  }

  @Test
  fun aWriteThatAlreadyReadTheClockIsStampedFromThatReading() {
    clock.epochMillis = 9_000

    assertEquals(2_000, stamps.updatedAt(previous = 1_000, now = 2_000))
  }
}

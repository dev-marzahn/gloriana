package dev.marzahn.gloriana.ui.connections

import java.awt.EventQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

class WriteGate : Clock {
  @Volatile
  private var held: CountDownLatch? = null

  @Volatile
  private var holder: Thread? = null
  private val caught = AtomicInteger()

  val writesCaught: Int get() = caught.get()

  fun hold() {
    caught.set(0)
    holder = Thread.currentThread()
    held = CountDownLatch(1)
  }

  fun release() {
    held?.countDown()
    held = null
  }

  override fun now(): Instant {
    held?.let { latch ->
      val thread = Thread.currentThread()
      check(thread != holder && !EventQueue.isDispatchThread()) {
        "a write on the test or UI thread would never be released"
      }
      caught.incrementAndGet()
      latch.await()
    }
    return Clock.System.now()
  }
}

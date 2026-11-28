package dev.marzahn.gloriana.desktop.tasks

import com.sun.jna.Pointer
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

private object NoLosses : KeepAliveLosses {
  override fun refused() = Unit

  override fun backgroundTimeRanOut() = Unit
}

private fun DesktopKeepAlive.startTask() = start(TaskNotice("Importing", "1 of 1", null), flowOf(null), NoLosses)

class DesktopKeepAliveTest {
  private val log = mutableListOf<String>()
  private var begun = 0
  private val activities = ProcessActivities { reason ->
    val number = ++begun
    log += "begin $number: $reason"
    ProcessActivity { log += "end $number" }
  }
  private val keepAlive = DesktopKeepAlive(activities)

  @Test
  fun `holds one activity from start to stop`() {
    keepAlive.startTask()
    keepAlive.stop()
    keepAlive.stop()
    assertEquals(listOf("begin 1: Gloriana is running a task the user started", "end 1"), log)
  }

  @Test
  fun `a second start ends the activity it replaces`() {
    keepAlive.startTask()
    keepAlive.startTask()
    keepAlive.stop()
    assertEquals(listOf("end 1", "end 2"), log.filter { it.startsWith("end") })
  }

  @Test
  fun `only macOS opts out of App Nap`() {
    assertEquals(ProcessActivities.None, ProcessActivities.forOs("Windows 11"))
    assertEquals(ProcessActivities.None, ProcessActivities.forOs("Linux"))
  }

  @Test
  fun `macOS begins and ends a real App Nap opt-out`() {
    assumeTrue(System.getProperty("os.name").lowercase().startsWith("mac"))
    val activities = assertIs<MacProcessActivities>(ProcessActivities.forOs(System.getProperty("os.name")))
    val held = List(2) { assertIs<MacProcessActivities.MacActivity>(activities.begin("test")) }
    held.forEach { assertNotEquals(0L, Pointer.nativeValue(it.handle)) }
    assertNotEquals(held[0].handle, held[1].handle)
    held.forEach { it.end() }
  }
}

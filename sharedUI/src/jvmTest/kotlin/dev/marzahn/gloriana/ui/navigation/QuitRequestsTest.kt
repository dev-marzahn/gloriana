package dev.marzahn.gloriana.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuitRequestsTest {
  private val calls = mutableListOf<String>()

  private fun QuitRequests.ask(name: String) = request(
    quit = { calls += "quit $name" },
    stay = { calls += "stay $name" },
  )

  @Test
  fun quitRunsTheRequestOnce() {
    val requests = QuitRequests()
    requests.ask("a")
    assertNotNull(requests.pending)

    requests.quit()
    requests.quit()

    assertNull(requests.pending)
    assertEquals(listOf("quit a"), calls)
  }

  @Test
  fun stayCancelsTheRequest() {
    val requests = QuitRequests()
    requests.ask("a")

    requests.stay()

    assertNull(requests.pending)
    assertEquals(listOf("stay a"), calls)
  }

  @Test
  fun aSecondRequestWhileOneIsOpenStaysAtOnce() {
    val requests = QuitRequests()
    requests.ask("a")
    requests.ask("b")

    requests.quit()

    assertEquals(listOf("stay b", "quit a"), calls)
  }
}

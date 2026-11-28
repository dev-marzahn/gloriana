package dev.marzahn.gloriana.ui.connections

import kotlin.test.Test
import kotlin.test.assertEquals

class HostOfTest {
  @Test
  fun theHostDropsTheSchemesOwnPortAndKeepsAnyOther() {
    assertEquals(
      listOf("api.anthropic.com", "localhost:8080", "example.com", "example.com:443", "[::1]:11434"),
      listOf(
        " https://API.Anthropic.com/v1 ",
        "http://localhost:8080/v1",
        "http://example.com:80",
        "http://example.com:443/v1",
        "http://[::1]:11434",
      ).map(::hostOf),
    )
  }

  @Test
  fun onlyHttpAndHttpsHaveADefaultPortToDrop() {
    assertEquals("example.com:443", hostOf("ftp://example.com:443"))
  }

  @Test
  fun anAddressWithoutASchemeShowsWhatComesBeforeItsPath() {
    assertEquals("api.example.com", hostOf(" api.example.com/v1"))
  }
}

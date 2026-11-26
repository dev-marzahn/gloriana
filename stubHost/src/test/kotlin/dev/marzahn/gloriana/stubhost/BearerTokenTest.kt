package dev.marzahn.gloriana.stubhost

import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BearerTokenTest {
  @Test
  fun `the key is what follows Bearer, in any case`() {
    assertEquals("MARKER-KEY", bearerToken(headersOf("Authorization", "Bearer MARKER-KEY")))
    assertEquals("MARKER-KEY", bearerToken(headersOf("Authorization", "bearer MARKER-KEY")))
  }

  @Test
  fun `another scheme, an empty token or no header is no key`() {
    assertNull(bearerToken(headersOf("Authorization", "Basic abc")))
    assertNull(bearerToken(headersOf("Authorization", "Bearer ")))
    assertNull(bearerToken(headersOf()))
  }
}

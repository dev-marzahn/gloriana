package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.MASK
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrubberTest {
  private fun secretsOf(connection: OpenedConnection) =
    checkNotNull(hostRequest(connection, HttpMethod.Post, "chat/completions", body = null)).secrets

  @Test
  fun everyExactOccurrenceOfTheKeyInABodyIsMasked() {
    val scrubber = Scrubber(secretsOf(openedConnection(ApiFormat.AnthropicMessages, key = "sk-1")))

    val echoed = scrubber.scrub("""{"error":"key sk-1 and sk-1 rejected"}""")

    assertEquals("""{"error":"key ••• and ••• rejected"}""", echoed)
  }

  @Test
  fun theKeyIsAlsoMaskedJsonEscapedAndPercentEncoded() {
    val scrubber = Scrubber(setOf("sk/a+b\"c"))

    assertEquals("$MASK $MASK $MASK", scrubber.scrub("sk\\/a+b\\\"c sk%2Fa%2Bb%22c sk/a+b\"c"))
  }

  @Test
  fun aCustomHeaderSentUnderAKeyHeaderNameIsASecretAndOtherCustomHeadersAreNot() {
    val connection = openedConnection(
      ApiFormat.OpenAiChatCompletions,
      key = null,
      headers = listOf(
        header("Authorization", "Bearer proxy-token"),
        header("api-key", "azure-key"),
        header("X-Mode", "off"),
      ),
    )

    assertEquals(setOf("Bearer proxy-token", "proxy-token", "azure-key"), secretsOf(connection))
    assertEquals("$MASK $MASK turn it off", Scrubber(secretsOf(connection)).scrub("proxy-token azure-key turn it off"))
  }

  @Test
  fun aConnectionWithNoKeyLeavesABodyAsItIs() {
    val scrubber = Scrubber(secretsOf(openedConnection(ApiFormat.AnthropicMessages, key = null)))

    assertEquals("body", scrubber.scrub("body"))
  }
}

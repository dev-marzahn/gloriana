package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.chat.Chat
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.MASK
import dev.marzahn.gloriana.logic.connection.api.ModelApiError
import dev.marzahn.gloriana.logic.connection.api.TEST_KEY
import dev.marzahn.gloriana.logic.keys.KeyFileResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class SharedFailure(
  val name: String,
  val expected: KClass<out ModelApiError>,
  val chat: suspend ReplyHarness.() -> Chat,
)

class ReplyFailureTest {
  @TempDir
  lateinit var directory: Path

  private val harness by lazy { ReplyHarness(directory) }

  @AfterEach
  fun close() = harness.close()

  private val sharedFailures = listOf(
    SharedFailure("key rejected", ModelApiError.KeyRejected::class) { chat("401") },
    SharedFailure("key forbidden", ModelApiError.KeyRejected::class) { chat("403") },
    SharedFailure("rate limited", ModelApiError.RateLimited::class) { chat("429") },
    SharedFailure("host error", ModelApiError.HostError::class) { chat("500") },
    SharedFailure("broken stream", ModelApiError.HostError::class) { chat("stream-error") },
    SharedFailure("unreachable", ModelApiError.Unreachable::class) { chat(baseUrl = "http://127.0.0.1:9/v1") },
    SharedFailure("timeout", ModelApiError.Timeout::class) { chat("delay-3000", streaming = false) },
  )

  private suspend fun failedWith(chat: Chat): ModelApiError {
    val messageId = harness.sent(chat.id)
    val live = assertIs<LiveReply.Failed>(harness.settled(chat.id))
    assertEquals(messageId, live.messageId)
    return assertIs<ReplyFailure.Host>(live.failure).error
  }

  @Test
  fun eachSharedFailureKeepsTheSentMessageAndShowsItsError() = runBlocking {
    sharedFailures.forEach { failure ->
      val chat = failure.chat(harness)

      val error = failedWith(chat)

      assertTrue(failure.expected.isInstance(error), "${failure.name}: $error")
      val stored = harness.library.messages(chat.id)
      assertEquals(listOf(MessageRole.Character, MessageRole.User, MessageRole.Character), stored.map { it.role })
      assertEquals("Hello, Ada.", stored[1].shown.text, failure.name)
      assertEquals(SwipeState.Failed, stored[2].shown.state, failure.name)
    }
  }

  @Test
  fun aKeyEchoedBackByTheHostIsMaskedInEveryFormat() = runBlocking {
    ApiFormat.entries.forEach { format ->
      val error = failedWith(harness.chat("key-echo", format))

      val body = assertIs<ModelApiError.KeyRejected>(error, "$format").body
      assertContains(body, MASK, message = "$format")
      assertFalse(TEST_KEY in body, "$format")
    }
  }

  @Test
  fun aRefusedReplyIsKeptAsAFailedSwipe() = runBlocking {
    val chat = harness.chat("refusal")
    val messageId = harness.sent(chat.id)

    assertEquals(LiveReply.Failed(messageId, ReplyFailure.Refused), harness.settled(chat.id))
    assertEquals(SwipeState.Failed, harness.library.messages(chat.id).last().shown.state)
  }

  @Test
  fun aStreamTheHostCutsOffIsKeptUnfinished() = runBlocking {
    val chat = harness.chat("cut-off")
    val messageId = harness.sent(chat.id)

    assertEquals(LiveReply.Unfinished(messageId, UnfinishedWhy.StreamEnded), harness.settled(chat.id))
    assertEquals(SwipeState.Unfinished, harness.library.messages(chat.id).last().shown.state)
  }

  @Test
  fun aReplyThatHitsTheTokenLimitIsComplete() = runBlocking {
    val chat = harness.chat("length")
    harness.sent(chat.id)

    harness.settled(chat.id)

    assertEquals(SwipeState.Complete, harness.library.messages(chat.id).last().shown.state)
  }

  @Test
  fun aKeyThatCannotBeOpenedRefusesTheSendAndAddsNoMessage() = runBlocking {
    val chat = harness.chat()
    val connectionId = checkNotNull(chat.connectionId)
    assertEquals(KeyFileResult.Done, harness.library.connections.vault.erase(connectionId))

    val result = assertIs<ReplyStartResult.KeysNotReady>(harness.writer.send(chat.id, "Hi", writingNotice))

    assertEquals(ModelApiError.KeysMissing(HeldSecrets.Key), result.error)
    assertEquals(listOf(MessageRole.Character), harness.library.messages(chat.id).map { it.role })
    assertEquals(emptyList(), harness.stub.recordedRequests())
  }
}

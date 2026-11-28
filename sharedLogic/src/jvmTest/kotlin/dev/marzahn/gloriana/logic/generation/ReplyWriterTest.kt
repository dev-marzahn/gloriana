package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.chat.ChatDeleteResult
import dev.marzahn.gloriana.logic.chat.ChatMessage
import dev.marzahn.gloriana.logic.chat.ChatWritePoint
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.Swipe
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.connection.api.ModelApi
import dev.marzahn.gloriana.stubhost.PLAIN_REPLY
import dev.marzahn.gloriana.logic.debug.FaultPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val GREETING = "Hello, User. I am Ada."
private const val SENT = "Hello, Ada."

class ReplyWriterTest {
  @TempDir
  lateinit var directory: Path

  private val harness by lazy { ReplyHarness(directory) }
  private val writer by lazy { harness.writer }

  @AfterEach
  fun close() = harness.close()

  private suspend fun messages(chatId: Uuid) = harness.library.messages(chatId)

  private suspend fun drafts() = harness.library.database.swipeDraftDao().all()

  private fun List<ChatMessage>.shown() = map { it.role to it.shown.text }

  private suspend fun arrivedSome(chatId: Uuid) = withTimeout(10.seconds) {
    writer.replies.first { (it[chatId] as? LiveReply.Writing)?.text?.text?.isNotEmpty() == true }
  }

  @Test
  fun aReplyIsStoredAsACompleteSwipeWithStreamingOnAndOff() = runBlocking {
    listOf(true, false).forEach { streaming ->
      val chat = harness.chat(streaming = streaming)

      val messageId = harness.sent(chat.id)

      assertNull(harness.settled(chat.id), "streaming=$streaming")
      val stored = messages(chat.id)
      assertEquals(
        listOf(MessageRole.Character to GREETING, MessageRole.User to SENT, MessageRole.Character to PLAIN_REPLY),
        stored.shown(),
      )
      assertEquals(Swipe(0, PLAIN_REPLY, "", SwipeState.Complete), stored.single { it.id == messageId }.shown)
    }
    assertEquals(emptyList(), drafts())
    assertEquals(emptyList(), harness.problems)
  }

  @Test
  fun reasoningIsKeptBesideTheText() = runBlocking {
    val chat = harness.chat("reasoning")

    harness.sent(chat.id)
    harness.settled(chat.id)

    val reply = messages(chat.id).last().shown
    assertEquals("Hello again from the stub host.", reply.text)
    assertEquals("The user greeted me, so I greet them back.", reply.reasoning)
  }

  @Test
  fun theDraftRowFollowsTheReplyWhileItStreams() = runBlocking {
    val chat = harness.chat("slow-150")

    val messageId = harness.sent(chat.id)

    val draft = withTimeout(10.seconds) {
      var found = drafts().singleOrNull()
      while (found == null || found.text.isEmpty()) found = drafts().singleOrNull()
      found
    }
    assertEquals(messageId, draft.messageId)
    assertTrue(PLAIN_REPLY.startsWith(draft.text))
    assertEquals(listOf(MessageRole.Character, MessageRole.User), messages(chat.id).map { it.role })
    harness.settled(chat.id)
  }

  @Test
  fun stopKeepsWhatArrivedAsAnUnfinishedSwipe() = runBlocking {
    val chat = harness.chat("slow-150")
    val messageId = harness.sent(chat.id)
    arrivedSome(chat.id)

    writer.stop(chat.id)

    assertEquals(LiveReply.Unfinished(messageId, UnfinishedWhy.Stopped), harness.settled(chat.id))
    val reply = messages(chat.id).last()
    assertEquals(SwipeState.Unfinished, reply.shown.state)
    assertTrue(reply.shown.text.isNotEmpty() && PLAIN_REPLY.startsWith(reply.shown.text))
    assertEquals(emptyList(), drafts())
  }

  @Test
  fun stopBeforeAnythingArrivedLeavesNoReplyButKeepsTheSentMessage() = runBlocking {
    val chat = harness.chat("delay-3000")
    harness.sent(chat.id)

    writer.stop(chat.id)

    assertNull(harness.settled(chat.id))
    assertEquals(
      listOf(MessageRole.Character to GREETING, MessageRole.User to SENT),
      messages(chat.id).shown(),
    )
    assertEquals(emptyList(), drafts())
  }

  @Test
  fun aRetryAddsASwipeToTheLastReplyAndAStoppedRetryKeepsThePreviousOne() = runBlocking {
    val chat = harness.chat("slow-20")
    val replyId = harness.sent(chat.id)
    harness.settled(chat.id)

    assertEquals(ReplyStartResult.Started(replyId), writer.retry(chat.id, writingNotice))
    harness.settled(chat.id)
    assertEquals(Swipe(1, PLAIN_REPLY, "", SwipeState.Complete), messages(chat.id).last().shown)

    writer.retry(chat.id, writingNotice)
    writer.stop(chat.id)
    harness.settled(chat.id)

    assertEquals(1, messages(chat.id).last().shown.index)
  }

  @Test
  fun aRetrySendsTheChatWithoutTheReplyBeingRetried() = runBlocking {
    val chat = harness.chat()
    harness.sent(chat.id)
    harness.settled(chat.id)

    writer.retry(chat.id, writingNotice)
    harness.settled(chat.id)

    val retried = harness.stub.recordedRequests().last().toString()
    assertTrue(PLAIN_REPLY !in retried, retried)
    assertTrue(SENT in retried, retried)
  }

  @Test
  fun aGreetingIsNotRetried() = runBlocking {
    val chat = harness.chat()

    assertEquals(ReplyStartResult.NothingToRetry, writer.retry(chat.id, writingNotice))
    assertEquals(emptyList(), drafts())
  }

  @Test
  fun aStoppedRetryOfAFailedReplyShowsTheFailureAgain() = runBlocking {
    val chat = harness.chat("delay-3000", streaming = false)
    val replyId = harness.sent(chat.id)
    val failed = assertIs<LiveReply.Failed>(harness.settled(chat.id))

    writer.retry(chat.id, writingNotice)
    writer.stop(chat.id)

    assertEquals(failed, harness.settled(chat.id))
    assertEquals(replyId, failed.messageId)
  }

  @Test
  fun theSwipeIsWrittenBeforeTheProcessIsLetGo() = runBlocking {
    val chat = harness.chat()
    val reachedWhenLetGo = CompletableDeferred<List<FaultPoint>>()
    harness.keepAlive.onStop = { reachedWhenLetGo.complete(harness.library.faults.reachedPoints.toList()) }

    harness.sent(chat.id)

    assertTrue(ChatWritePoint.DraftSwipeWritten in withTimeout(10.seconds) { reachedWhenLetGo.await() })
  }

  @Test
  fun aChatDeletedElsewhereWhileItsReplyIsWrittenEndsTheReply() = runBlocking {
    val chat = harness.chat("slow-150")
    harness.sent(chat.id)
    arrivedSome(chat.id)

    assertEquals(ChatDeleteResult.Deleted, harness.library.repository.delete(chat.id))

    assertNull(harness.settled(chat.id))
    assertEquals(ReplyStartResult.ChatGone, writer.send(chat.id, "Hi", writingNotice))
    assertEquals(emptyList(), drafts())
    assertEquals(emptyList(), harness.problems)
  }

  @Test
  fun aReplyThatFailsBecauseItsChatWasDeletedLeavesNothingShownAndLogsNothing() = runBlocking {
    lateinit var deleting: ReplyHarness
    var chatId: Uuid? = null
    val brokenDirectory = Files.createDirectories(directory.resolve("deleting"))
    deleting = ReplyHarness(brokenDirectory) {
      ModelApi { _, _ ->
        flow {
          deleting.library.repository.delete(checkNotNull(chatId))
          error("The chat went away")
        }
      }
    }
    deleting.use {
      val chat = it.chat()
      chatId = chat.id
      it.sent(chat.id)

      assertNull(it.settled(chat.id))
      assertEquals(emptyList(), it.problems)
    }
  }

  @Test
  fun aProblemInsideGlorianaIsNotShownAsTheHosts() = runBlocking {
    val brokenDirectory = Files.createDirectories(directory.resolve("broken"))
    val broken = ReplyHarness(brokenDirectory) { ModelApi { _, _ -> flow { error("Broken") } } }
    broken.use {
      val chat = it.chat()
      val messageId = it.sent(chat.id)

      assertEquals(LiveReply.Failed(messageId, ReplyFailure.GlorianaProblem), it.settled(chat.id))
      assertEquals(listOf("A reply could not be written"), it.problems)
    }
  }

  @Test
  fun thereIsNothingToRetryInAChatWithNoMessages() = runBlocking {
    val chat = harness.chat()
    val empty = harness.library.started(
      harness.library.newChat(harness.library.character(name = "Quiet", firstMessage = ""))
        .copy(connectionId = chat.connectionId),
    )

    assertEquals(ReplyStartResult.NothingToRetry, writer.retry(empty.id, writingNotice))
  }

  @Test
  fun oneReplyAtATimeAndTheChatCannotBeDeletedWhileItIsWritten() = runBlocking {
    val chat = harness.chat("slow-100")
    harness.sent(chat.id)

    assertEquals(ReplyStartResult.ReplyRunning, writer.send(chat.id, "Again", writingNotice))
    assertEquals(ReplyStartResult.ReplyRunning, writer.retry(chat.id, writingNotice))
    assertEquals(ReplyDeleteResult.ReplyRunning, writer.delete(chat.id))

    harness.settled(chat.id)
    assertEquals(ReplyDeleteResult.Done(ChatDeleteResult.Deleted), writer.delete(chat.id))
  }

  @Test
  fun aReplyCutOffByTheBackgroundTimeIsKeptUnfinished() = runBlocking {
    val chat = harness.chat("slow-150")
    val messageId = harness.sent(chat.id)
    arrivedSome(chat.id)

    checkNotNull(harness.keepAlive.losses).backgroundTimeRanOut()

    assertEquals(LiveReply.Unfinished(messageId, UnfinishedWhy.BackgroundTimeRanOut), harness.settled(chat.id))
    assertEquals(SwipeState.Unfinished, messages(chat.id).last().shown.state)
    assertEquals(emptyList(), drafts())
  }

  @Test
  fun aReplyNotSavedBecauseStorageIsFullSaysSo() = runBlocking {
    val chat = harness.chat()
    harness.library.faults.failure = { IOException("No space left on device") }
    harness.library.faults.failing = ChatWritePoint.DraftSwipeWritten
    val messageId = harness.sent(chat.id)

    assertEquals(LiveReply.Failed(messageId, ReplyFailure.NotSavedStorageFull), harness.settled(chat.id))
  }

  @Test
  fun aRetryOfAReplyWhoseEmptyDraftWasNotSavedRemovesItAndHasNothingToRetry() = runBlocking {
    val chat = harness.chat()
    harness.library.replyRows.startReply(chat.id, SENT, chat.characterIds.single())

    assertEquals(ReplyStartResult.NothingToRetry, writer.retry(chat.id, writingNotice))
    assertEquals(listOf(MessageRole.Character to GREETING, MessageRole.User to SENT), messages(chat.id).shown())
    assertEquals(emptyList(), drafts())
  }

  @Test
  fun aReplyKilledBeforeItsSwipeIsWrittenComesBackUnfinishedFromItsLastDraft() = runBlocking {
    val chat = harness.chat("slow-150")
    harness.library.faults.failing = ChatWritePoint.DraftSwipeWritten
    val messageId = harness.sent(chat.id)

    assertEquals(LiveReply.Failed(messageId, ReplyFailure.NotSaved), harness.settled(chat.id))
    assertEquals(listOf("A reply could not be saved"), harness.problems)
    val saved = drafts().single().text

    harness.library.faults.failing = null
    assertTrue(harness.library.leftoverDrafts.keepAsUnfinished())

    val kept = messages(chat.id).last()
    assertEquals(messageId, kept.id)
    assertEquals(Swipe(0, saved, "", SwipeState.Unfinished), kept.shown)
    assertTrue(saved.isNotEmpty() && PLAIN_REPLY.startsWith(saved))
  }

  @Test
  fun aRetryAfterAReplyThatWasNotSavedKeepsItsDraftFirst() = runBlocking {
    val chat = harness.chat("slow-150")
    harness.library.faults.failing = ChatWritePoint.DraftSwipeWritten
    val messageId = harness.sent(chat.id)
    harness.settled(chat.id)
    harness.library.faults.failing = null

    assertEquals(ReplyStartResult.Started(messageId), writer.retry(chat.id, writingNotice))
    harness.settled(chat.id)

    assertEquals(Swipe(1, PLAIN_REPLY, "", SwipeState.Complete), messages(chat.id).last().shown)
    assertTrue(harness.library.database.swipeDraftDao().swipeExists(messageId, 0))
    assertEquals(emptyList(), drafts())
  }

  @Test
  fun aChatWithNoConnectionWritesNothing() = runBlocking {
    val unconnected = harness.library.started(harness.library.newChat(harness.library.character("Lone")))

    assertIs<ReplyStartResult.NoConnection>(writer.send(unconnected.id, "Hi", writingNotice))
    assertEquals(listOf(MessageRole.Character), messages(unconnected.id).map { it.role })
  }
}

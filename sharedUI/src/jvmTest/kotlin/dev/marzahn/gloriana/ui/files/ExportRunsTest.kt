package dev.marzahn.gloriana.ui.files

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.files.ByteSink
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.logic.files.ExportWrite
import dev.marzahn.gloriana.ui.messages.AppMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private const val QUIET_MILLIS = 200L

private object NowhereDestination : ExportDestination {
  override fun write(content: (ByteSink) -> Unit): ExportWrite = error("Never written in these tests")
}

class ExportRunsTest {
  private val messages = AppMessages()
  private val runs = ExportRuns(ApplicationScope(Dispatchers.Unconfined) { throw it }, messages) { _, _ -> }
  private val id = Uuid.random()
  private val failed = ExportOutcome.NotSaved(ExportProblem(ExportFailure.CantWrite, incompleteFileLeft = false))

  private fun nextMessage() = runBlocking { withTimeoutOrNull(QUIET_MILLIS) { messages.messages.first() } }

  @Test
  fun aFailureWhileThePageIsShownLeavesTheNoteAndNoSnackbar() {
    runs.pageShown(id)

    runs.start(id, SaveTarget(NowhereDestination, "a.json")) { failed }

    assertIs<ExportState.NotSaved>(runBlocking { runs.state(id).first() })
    assertNull(nextMessage())
  }

  @Test
  fun aFailureAfterThePageIsHiddenPostsASnackbar() {
    runs.pageShown(id)
    runs.pageHidden(id)

    runs.start(id, SaveTarget(NowhereDestination, "a.json")) { failed }

    assertNotNull(nextMessage())
  }
}

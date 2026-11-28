package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredRowTest {
  private fun stays(value: String) = flow {
    emit(value)
    awaitCancellation()
  }

  private fun <R : Any> TestScope.seenWhileCollecting(rows: Flow<R>, during: () -> Unit = {}) =
    mutableListOf<R>().also { seen ->
      val collecting = launch(start = CoroutineStart.UNDISPATCHED) { rows.collect { seen.add(it) } }
      testScheduler.runCurrent()
      during()
      testScheduler.runCurrent()
      collecting.cancel()
    }

  @Test
  fun aFailedReadStaysCouldNotLoadAndRetryShowsLoadingBeforeTheRow() = runTest {
    val reads = StoredReads.rows<String>()
    var attempt = 0
    val read = flow<String?> {
      if (attempt++ == 0) error("disk unreadable")
      emit("Work")
      awaitCancellation()
    }

    val seen = seenWhileCollecting(reads.follow("work") { read }) { reads.retry() }

    assertEquals(
      listOf<StoredRow<String>>(
        StoredValue.Loading,
        StoredValue.CouldNotLoad,
        StoredValue.Loading,
        StoredValue.Found("Work"),
      ),
      seen,
    )
  }

  @Test
  fun aMissingRowIsGone() = runTest {
    val rows = StoredReads.rows<String>().follow("work") { flowOf(null) }.take(2).toList()

    assertEquals(listOf(StoredValue.Loading, StoredRow.Gone), rows)
  }

  @Test
  fun collectingAgainStartsFromTheLastRowInsteadOfLoading() = runTest {
    val reads = StoredReads.values<String>()
    seenWhileCollecting(reads.follow("work") { stays("Work") })

    val again = seenWhileCollecting(reads.follow("work") { stays("Work") })

    assertEquals(StoredValue.Found("Work"), again.first())
  }

  @Test
  fun anotherRowOrARetryStartsFromLoadingAgain() = runTest {
    val reads = StoredReads.values<String>()
    seenWhileCollecting(reads.follow("work") { stays("Work") })

    val other = seenWhileCollecting(reads.follow("home") { stays("Home") })
    reads.retry()
    val retried = seenWhileCollecting(reads.follow("home") { stays("Home") })

    assertEquals(StoredValue.Loading, other.first())
    assertEquals(StoredValue.Loading, retried.first())
  }

  @Test
  fun workAlongsideAFlowRunsOnlyWhileTheFlowIsCollected() = runTest {
    val working = MutableStateFlow(false)
    val work = flow<Unit> { awaitCancellation() }
      .onStart { working.value = true }
      .onCompletion { working.value = false }
    val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
      MutableStateFlow(1).alongside(work).collect {}
    }
    testScheduler.runCurrent()
    assertTrue(working.value)

    collecting.cancel()
    testScheduler.runCurrent()

    assertFalse(working.value)
  }
}

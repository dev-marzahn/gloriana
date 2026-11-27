package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.SecretsState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val id = Uuid.random()

private fun shownWith(name: String): ConnectionPageState.Shown {
  val saved = Connection(
    id,
    ConnectionFields(name, ApiFormat.OpenAiChatCompletions, baseUrl = "https://api.example.com/v1", model = "model-1"),
    key = null,
    headers = emptyList(),
  )
  return ConnectionPageState.Shown(
    saved = saved,
    isDefault = false,
    copy = EditedCopy.of(ConnectionForm.of(saved)),
    errors = FormErrors.None,
    secrets = SecretsState.Usable,
    globalSamplers = Samplers(),
    activity = PageActivity.Saving,
    test = null,
    note = null,
    modelListNote = null,
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoredRowWaitTest {
  private fun TestScope.waitingOn(state: MutableStateFlow<ConnectionPageState>): () -> Boolean {
    val context = PageContext(MutableStateFlow(PageSession()), this, Channel(Channel.BUFFERED), state)
    var done = false
    launch {
      context.storedRowShows(
        after = ConnectionForm.of(checkNotNull(shownWith("Home").saved)),
        before = ConnectionForm.of(checkNotNull(shownWith("Work").saved)),
      )
      done = true
    }
    runCurrent()
    return { done }
  }

  @Test
  fun theWaitEndsOnceTheLiveRowShowsTheSave() = runTest {
    val state = MutableStateFlow<ConnectionPageState>(shownWith("Work"))
    val done = waitingOn(state)
    assertFalse(done())

    state.value = shownWith("Home")
    runCurrent()

    assertTrue(done())
  }

  @Test
  fun aLiveRowThatNeverLeavesThePreSaveValueEndsTheWaitWithinTwoSeconds() = runTest {
    val state = MutableStateFlow<ConnectionPageState>(shownWith("Work"))
    val done = waitingOn(state)

    advanceTimeBy(1.9.seconds)
    assertFalse(done())
    advanceTimeBy(0.2.seconds)

    assertTrue(done())
  }
}

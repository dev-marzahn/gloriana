package dev.marzahn.gloriana.logic.connection.api

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EventStreamTest {
  private fun dataOf(channel: ByteReadChannel): List<String> = runBlocking {
    buildList { channel.readEventData { add(it) } }
  }

  private fun dataOf(text: String) = dataOf(ByteReadChannel(text))

  @Test
  fun eachBlankLineEndsAnEventAndItsDataLinesJoinWithNewlines() {
    assertEquals(listOf("one", "two\nthree"), dataOf("data: one\n\ndata: two\ndata:three\n\n"))
  }

  @Test
  fun crlfAndALoneCrEndLinesToo() {
    assertEquals(listOf("a", "b"), dataOf("data: a\r\n\r\ndata: b\r\r"))
  }

  @Test
  fun commentsEventNamesAndOtherFieldsAreSkipped() {
    assertEquals(listOf("{}"), dataOf(": keep-alive\nevent: message_start\nid: 7\nretry: 10\ndata: {}\n\n"))
  }

  @Test
  fun aLeadingByteOrderMarkIsDropped() {
    val byteOrderMark = Char(0xFEFF)

    assertEquals(listOf("x"), dataOf("${byteOrderMark}data: x\n\n"))
  }

  @Test
  fun anEventTheStreamEndsBeforeItsBlankLineIsNotDelivered() {
    assertEquals(listOf("whole"), dataOf("data: whole\n\ndata: cut"))
  }

  @Test
  fun aCharacterSplitAcrossWritesArrivesWhole() = runBlocking {
    val channel = ByteChannel()
    val bytes = "data: café ☕\n\n".encodeToByteArray()
    val splitInsideTheCup = bytes.size - 4
    launch {
      channel.writeByteArray(bytes.copyOfRange(0, splitInsideTheCup))
      channel.flush()
      channel.writeByteArray(bytes.copyOfRange(splitInsideTheCup, bytes.size))
      channel.close()
    }

    assertEquals(listOf("café ☕"), buildList { channel.readEventData { add(it) } })
  }
}

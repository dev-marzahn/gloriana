package dev.marzahn.gloriana.stubhost

import dev.marzahn.gloriana.stubhost.PartKind.REASONING
import dev.marzahn.gloriana.stubhost.PartKind.TEXT
import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentsTest {
  private val request = ModelRequest(model = FormatReader.TEST_MODEL)
  private val twoOfEach = Reply(
    Segment.Reasoning("First thought. "),
    Segment.Reasoning("Second thought."),
    Segment.Text("First words. "),
    Segment.Text("Second words."),
  )

  @Test
  fun `every format keeps every segment, whole and streamed alike`() {
    val expected = Received(
      listOf(
        ReceivedPart(REASONING, "First thought. Second thought."),
        ReceivedPart(TEXT, "First words. Second words."),
      ),
      ReceivedTokens(input = 10, output = 8, total = 18, reasoning = 4),
    )
    FormatReader.all.forEach { reader ->
      val format = reader.format
      val whole = parseObject(format.whole(twoOfEach, request))
      assertEquals(reader.asReported(expected), reader.readWhole(whole), format.path)
      val streamed = format.stream(twoOfEach, request.copy(streamOptions = StreamOptions(includeUsage = true)))
      assertEquals(reader.asReported(expected), reader.readStream(streamed), format.path)
    }
  }
}

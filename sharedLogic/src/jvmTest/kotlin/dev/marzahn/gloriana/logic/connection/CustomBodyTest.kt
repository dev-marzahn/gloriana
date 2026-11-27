package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.api.json
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomBodyTest {
  @Test
  fun objectsMergeKeyByKeyAtEveryDepth() {
    val generated = json("""{"model":"m","stream_options":{"include_usage":true},"a":{"b":{"c":1,"d":2}}}""")
    val custom = json("""{"stream_options":{"extra":1},"a":{"b":{"d":3}}}""")

    assertEquals(
      json("""{"model":"m","stream_options":{"include_usage":true,"extra":1},"a":{"b":{"c":1,"d":3}}}"""),
      generated.mergedWith(custom),
    )
  }

  @Test
  fun anArrayOrAValueReplacesTheGeneratedOne() {
    val generated = json("""{"messages":[1,2],"store":false,"thinking":{"type":"off"}}""")
    val custom = json("""{"messages":[3],"store":true,"thinking":"plain"}""")

    assertEquals(json("""{"messages":[3],"store":true,"thinking":"plain"}"""), generated.mergedWith(custom))
  }

  @Test
  fun nullRemovesTheKeyAtAnyDepthAndAddsNothing() {
    val generated = json("""{"max_tokens":10,"stream_options":{"include_usage":true}}""")
    val custom = json("""{"max_tokens":null,"stream_options":{"include_usage":null},"new":{"gone":null,"kept":1}}""")

    assertEquals(json("""{"stream_options":{},"new":{"kept":1}}"""), generated.mergedWith(custom))
  }

  @Test
  fun streamIsReadOnlyAsAJsonBoolean() {
    assertEquals(StreamValue.On, json("""{"stream":true}""").streamValue())
    assertEquals(StreamValue.Off, json("""{"stream":false}""").streamValue())
    assertEquals(StreamValue.Off, json("""{}""").streamValue())
    listOf("""{"stream":"true"}""", """{"stream":{}}""", """{"stream":[]}""", """{"stream":null}""", """{"stream":1}""")
      .forEach { assertEquals(StreamValue.NotBoolean, json(it).streamValue(), it) }
  }
}

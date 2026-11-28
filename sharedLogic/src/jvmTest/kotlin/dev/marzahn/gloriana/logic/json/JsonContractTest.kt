package dev.marzahn.gloriana.logic.json

import dev.marzahn.gloriana.logic.storage.StoredJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonContractTest {
  private val values = assertIs<JsonObject>(
    parsedObject("""{"on": true, "quoted": "true", "count": 3, "quotedCount": "3", "name": "Ada", "number": 1}"""),
  )

  @Test
  fun aQuotedLiteralIsAStringNeverABooleanOrANumber() {
    assertEquals(true, values["on"].asBoolean())
    assertNull(values["quoted"].asBoolean())
    assertEquals(3, values.int("count"))
    assertNull(values.int("quotedCount"))
    assertNull(values.long("quotedCount"))
    assertEquals("Ada", values.string("name"))
    assertNull(values.string("number"))
  }

  @Test
  fun quotedOrBareReadsTakeEither() {
    assertEquals(true, values.quotedOrBareBoolean("quoted"))
    assertEquals(3, values.quotedOrBareInt("quotedCount"))
    assertEquals(3L, values.quotedOrBareLong("count"))
    assertNull(values.quotedOrBareInt("name"))
  }

  @Test
  fun textThatIsNotTheExpectedJsonDecodesToNothing() {
    assertNull(parsedElement("{"))
    assertIs<JsonParse.Failed>(parseJson("{"))
    assertNull(parsedObject("[1]"))
    assertNull(StoredJson.decodedOrNull<Map<String, String>>("""{"a": 1}"""))
    assertNull(StoredJson.decodedOrNull<JsonObject>("not json"))
  }
}

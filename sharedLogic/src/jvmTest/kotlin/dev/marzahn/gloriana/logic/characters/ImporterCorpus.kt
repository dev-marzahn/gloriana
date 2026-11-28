package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.characters.CardFixtures.base64
import dev.marzahn.gloriana.logic.characters.CardFixtures.image
import dev.marzahn.gloriana.logic.characters.CardFixtures.png
import dev.marzahn.gloriana.logic.characters.CardFixtures.zip
import dev.marzahn.gloriana.logic.files.ByteSource
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.zip.ZipEntry

private const val PNG_IHDR_DATA_BYTE = 18

class CorpusFile(override val name: String, val bytes: ByteArray) : PickedFile {
  override fun open(): OpenedFile = bytes.inputStream().let { input ->
    OpenedFile.Opened(
      object : ByteSource {
        override fun read(into: ByteArray, offset: Int, length: Int) = input.read(into, offset, length)

        override fun close() = input.close()
      },
    )
  }
}

object ImporterCorpus {
  private fun card(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

  private val ccv1 = card(
    """
    {"name": "Wren", "description": "A lighthouse keeper.", "personality": "Patient",
     "scenario": "A storm night.", "first_mes": "You made it, {{user}}.", "mes_example": "<START>\n{{char}}: Tea?"}
    """,
  )

  private val ccv2 = card(
    """
    {"spec": "chara_card_v2", "spec_version": "2.0",
     "name": "Old copy", "description": "stale", "talkativeness": "0.5", "fav": true, "MARKER-OUTSIDE": "kept",
     "data": {
       "name": "Brannoc", "description": "A smith of the hill country.", "personality": "Gruff",
       "scenario": "The forge at dusk.", "first_mes": "Mind the sparks.", "mes_example": "{{char}}: Hm.",
       "creator_notes": "Tested with long chats.", "system_prompt": "Stay in role. {{original}}",
       "post_history_instructions": "{{original}} Keep it short.", "alternate_greetings": ["Back again?", "Quiet day."],
       "tags": ["smith", "fantasy"], "creator": "ashfall", "character_version": "1.2",
       "extensions": {
         "depth_prompt": {"prompt": "Brannoc limps.", "depth": 4, "role": "system", "other_app_weight": 2},
         "other_app": {"mood": 3}
       }
     }}
    """,
  )

  private val ccv3 = card(
    """
    {"spec": "chara_card_v3", "spec_version": "3.0",
     "data": {
       "name": "Isolde of the Reach", "nickname": "Isolde", "description": "A cartographer.",
       "personality": "Curious", "scenario": "A map room.", "first_mes": "Hold this corner.",
       "mes_example": "{{char}}: North is up.", "creator_notes": "English notes.",
       "creator_notes_multilingual": {"en": "English notes.", "de": "Deutsche Hinweise."},
       "system_prompt": "{{original}}", "post_history_instructions": "", "alternate_greetings": ["Lost?"],
       "group_only_greetings": ["Everyone, gather round."], "tags": ["maps"], "creator": "lindqvist",
       "character_version": "3", "source": ["https://example.com/isolde"], "creation_date": 1700000000,
       "modification_date": 1700100000,
       "assets": [
         {"type": "background", "uri": "https://example.com/room.png", "name": "room", "ext": "png", "x-note": "dusk"},
         {"type": "other", "name": "no uri", "ext": "bin"}
       ],
       "extensions": {}
     }}
    """,
  )

  private val ccv3Png = card(
    """
    {"spec": "chara_card_v3", "spec_version": "3.0",
     "data": {
       "name": "Mara", "description": "<b>Bold</b> and <mark>marked</mark>.\n# Heading", "personality": "",
       "scenario": "", "first_mes": "", "mes_example": "", "alternate_greetings": ["First", "Second"],
       "group_only_greetings": [], "tags": ["shared"], "creator": "", "character_version": "",
       "creator_notes": "<script>fetch('http://stub/x')</script><table><tr><td>prose</td></tr></table> **bold**",
       "system_prompt": "", "post_history_instructions": "",
       "assets": [
         {"type": "icon", "uri": "ccdefault:", "name": "main", "ext": "png"},
         {"type": "background", "uri": "__asset:0", "name": "sky", "ext": "png"}
       ],
       "extensions": {}
     }}
    """,
  )

  private val ccv3Charx = card(
    """
    {"spec": "chara_card_v3", "spec_version": "3.0",
     "data": {
       "name": "Tamsin", "description": "A ferrywoman.", "personality": "Dry", "scenario": "The river.",
       "first_mes": "Coin first.", "mes_example": "", "alternate_greetings": [], "group_only_greetings": [],
       "tags": ["shared", "river"], "creator": "oarlock", "character_version": "1.0",
       "creator_notes": "", "system_prompt": "", "post_history_instructions": "",
       "character_book": {"name": "River lore", "entries": [{"keys": ["ford"], "content": "The ford floods in spring."}]},
       "assets": [
         {"type": "icon", "uri": "embeded://assets/icon/images/side.png", "name": "side", "ext": "png"},
         {"type": "icon", "uri": "embeded://assets/icon/images/main.png", "name": "main", "ext": "png"},
         {"type": "emotion", "uri": "embeded://assets/emotion/images/smile.png", "name": "smile", "ext": "png"}
       ],
       "extensions": {}
     }}
    """,
  )

  val ccv1Json = CorpusFile("wren.json", CardFixtures.json(ccv1))
  val ccv1Png = CorpusFile("wren.png", png(image(10), "chara" to base64(ccv1)))
  val ccv2Json = CorpusFile("brannoc.json", CardFixtures.json(ccv2))
  val ccv2Png = CorpusFile("brannoc.png", png(image(20), "chara" to base64(ccv2)))
  val ccv3Json = CorpusFile("isolde.json", CardFixtures.json(ccv3))
  val ccv3PngFile = CorpusFile(
    "mara.png",
    png(image(30), "ccv3" to base64(ccv3Png), "chara-ext-asset_:0" to base64(image(31, width = 3))),
  )
  val charx = CorpusFile(
    "tamsin.charx",
    zip(
      "card.json" to CardFixtures.json(ccv3Charx),
      "assets/icon/images/main.png" to image(40),
      "assets/icon/images/side.png" to image(41),
      "assets/emotion/images/smile.png" to image(42),
      "assets/unused.bin" to byteArrayOf(1, 2, 3),
    ),
  )

  val onePerFormat = listOf(ccv1Json, ccv1Png, ccv2Json, ccv2Png, ccv3Json, ccv3PngFile, charx)

  val charxUpdated = CorpusFile(
    "tamsin-1.1.charx",
    zip(
      "card.json" to CardFixtures.json(
        card(
          """
          {"spec": "chara_card_v3", "spec_version": "3.0",
           "data": {"name": "Tamsin", "creator": "oarlock", "character_version": "1.1", "description": "Retired.",
             "alternate_greetings": ["Evening."], "tags": ["river"],
             "assets": [{"type": "icon", "uri": "embeded://main.png", "name": "main", "ext": "png"}]}}
          """,
        ),
      ),
      "main.png" to image(43),
    ),
  )

  val withPronouns = CorpusFile(
    "pronouns.json",
    CardFixtures.json(
      card(
        """
        {"spec": "chara_card_v3", "spec_version": "3.0",
         "data": {"name": "Ash", "creator": "glade", "description": "First.",
           "extensions": {"gloriana": {"pronouns": {"sub": "they", "obj": "them", "poss": "their"}}}}}
        """,
      ),
    ),
  )

  val withoutPronounsUpdate = CorpusFile(
    "pronouns-updated.json",
    CardFixtures.json(
      card(
        """
        {"spec": "chara_card_v3",
         "data": {"name": " Ash${'\t'}", "creator": "glade${"\u00a0"}", "description": "Second."}}
        """,
      ),
    ),
  )

  val sameNameOtherCreator = CorpusFile(
    "mara-other.png",
    png(image(32), "ccv3" to base64(card("""{"spec": "chara_card_v3", "data": {"name": "Mara", "creator": "x"}}"""))),
  )

  val newerSpecVersion = CorpusFile(
    "newer.json",
    CardFixtures.json(
      card("""{"spec": "chara_card_v3", "spec_version": "4.0", "data": {"name": "Next", "MARKER-NEWER": 1}}"""),
    ),
  )

  val unknownSpec = CorpusFile(
    "unknown.json",
    CardFixtures.json(
      card("""{"spec": "other_card", "data": {"name": "Odd", "description": 7, "MARKER-UNKNOWN-SPEC": 1}}"""),
    ),
  )

  val bothChunksDiffer = CorpusFile(
    "both.png",
    png(
      image(50),
      "chara" to base64(card("""{"spec": "chara_card_v2", "data": {"name": "From chara"}}""")),
      "ccv3" to base64(card("""{"spec": "chara_card_v3", "data": {"name": "From ccv3"}}""")),
    ),
  )

  val singleCards = onePerFormat + listOf(
    charxUpdated,
    withPronouns,
    withoutPronounsUpdate,
    sameNameOtherCreator,
    newerSpecVersion,
    unknownSpec,
    bothChunksDiffer,
  )

  val brokenPng = CorpusFile("broken.png", ccv1Png.bytes.copyOf(ccv1Png.bytes.size - 20))
  val pngWithBadCrc = CorpusFile("bad-crc.png", ccv1Png.bytes.copyOf().also { it[PNG_IHDR_DATA_BYTE]++ })
  val unparsableJson = CorpusFile("broken.json", """{"name": "Wren", """.toByteArray())
  val unpackableCharx = CorpusFile("broken.charx", byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(64) { 7 })
  val charxWithoutCard = CorpusFile("nocard.charx", zip("assets/main.png" to image(1)))

  val pngWithoutCard = CorpusFile("plain.png", image(60))
  val lorebook = CorpusFile("lorebook.json", """{"name": "World", "entries": {"0": {"key": ["a"]}}}""".toByteArray())
  val preset = CorpusFile("preset.json", """{"name": "Preset", "temperature": 1, "prompts": []}""".toByteArray())
  val zipWithoutCard = CorpusFile("notes.zip", zip("readme.txt" to "hi".toByteArray()))
  val textFile = CorpusFile("notes.txt", "Just some notes.".toByteArray())

  private val escape = "../MARKER-ESCAPE.txt" to "x".toByteArray()
  private val absolute = "/tmp/MARKER-ABSOLUTE.txt" to "x".toByteArray()
  private val zeros = "zeros.bin" to ByteArray(4 * 1024 * 1024)
  private val ccv1Card = "card.json" to CardFixtures.json(ccv1)

  val zipEscape = CorpusFile("escape.zip", zip(escape, method = ZipEntry.STORED))
  val charxEscape = CorpusFile("escape.charx", zip(ccv1Card, escape, method = ZipEntry.STORED))
  val charxAbsolute = CorpusFile("absolute.charx", zip(ccv1Card, absolute, method = ZipEntry.STORED))
  val zipBomb = CorpusFile("bomb.zip", zip(zeros))
  val charxBomb = CorpusFile("bomb.charx", zip(ccv1Card, zeros))
}

package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.ConnectionTemplate
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.JsonFault
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.TextPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionFormTest {
  private fun samplers(vararg texts: Pair<Sampler, String>) = SamplerTexts(texts.toMap())

  private fun typed(text: String) = KeyEdit.typing(SecretText(text))

  @Test
  fun typingNothingIsEnteringAKeyNotTypingOne() {
    assertEquals(KeyEdit.Entering, typed(""))
    assertEquals(KeyEdit.Typed(SecretText("s")), typed("s"))
  }

  @Test
  fun aTemplateFillsTheNameFormatAndBaseUrl() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenRouter)

    assertEquals("OpenRouter", form.name)
    assertEquals(ApiFormat.OpenAiChatCompletions, form.format)
    assertEquals("https://openrouter.ai/api/v1", form.baseUrl)
    assertEquals(ConnectionFields.DEFAULT_CONTEXT_SIZE.toString(), form.contextSize)
    assertEquals(ConnectionFields.DEFAULT_MAX_REPLY_TOKENS.toString(), form.maxReplyTokens)
    assertEquals(ConnectionFields.DEFAULT_TIMEOUT_SECONDS.toString(), form.timeout)
  }

  @Test
  fun fromNothingStartsWithNoFormatAndRefusesToSaveWithoutOne() {
    val refused = assertIs<FormCheck.Refused>(ConnectionForm.fromNothing.check())

    assertEquals(FieldError.FormatEmpty, refused.errors[FormField.Format])
  }

  @Test
  fun numbersCompareByValue() {
    val saved = ConnectionForm.of(ConnectionTemplate.Anthropic).copy(samplers = samplers(Sampler.Temperature to "0.7"))

    val sameValues = saved.copy(
      samplers = samplers(Sampler.Temperature to "0.70"),
      contextSize = "0" + saved.contextSize,
    )
    assertTrue(saved.sameAs(sameValues))
    assertFalse(saved.sameAs(saved.copy(samplers = samplers(Sampler.Temperature to "0.8"))))
  }

  @Test
  fun aBlankHeaderRowIsDroppedAndChangesNothing() {
    val form = ConnectionForm.of(ConnectionTemplate.Anthropic)
    val withBlank = form.withHeader()

    assertTrue(form.sameAs(withBlank))
    val draft = assertIs<FormCheck.Ready>(withBlank.copy(model = "claude").check()).draft
    assertTrue(draft.headers.isEmpty())
  }

  @Test
  fun aSavedKeyIsKeptUntilReplacedOrRemoved() {
    val saved = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(keyStored = true, key = KeyEdit.Kept, model = "gpt")

    assertEquals(KeyInput.Kept, saved.readyDraft().key)
    assertEquals(KeyInput.Kept, saved.copy(key = typed("")).readyDraft().key)
    assertEquals(KeyInput.Removed, saved.copy(key = KeyEdit.Removed).readyDraft().key)
    assertIs<KeyInput.Entered>(saved.copy(key = typed("sk-new")).readyDraft().key)
  }

  @Test
  fun clearingAReplacementOfAStoredHeaderValueKeepsTheStoredValue() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(
      model = "gpt",
      headers = listOf(HeaderRow(0, "X-Org", "X-Org", ValueEdit.Entering)),
    )
    val saved = form.copy(headers = listOf(HeaderRow(0, "X-Org", "X-Org", ValueEdit.Kept)))

    assertEquals(HeaderValueInput.Kept("X-Org"), form.readyDraft().headers.single().value)
    assertTrue(form.sameAs(saved))
  }

  @Test
  fun aRenamedSavedHeaderKeepsItsStoredValue() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(
      model = "gpt",
      headers = listOf(HeaderRow(0, "X-Team", "X-Org", ValueEdit.Kept)),
    )

    val header = form.readyDraft().headers.single()
    assertEquals("X-Team", header.name)
    assertEquals(HeaderValueInput.Kept("X-Org"), header.value)
  }

  @Test
  fun samplersTheFormatDoesNotTakeAreKeptOnSaveForASwitchBack() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(
      model = "gpt",
      samplers = samplers(Sampler.Seed to "7", Sampler.TopK to "40"),
      format = ApiFormat.OpenAiResponses,
    )

    val samplers = form.readyDraft().fields.samplers
    assertEquals(7L, samplers.seed)
    assertEquals(40L, samplers.topK)
  }

  @Test
  fun aSamplerTheFormatHidesStillCountsAsAChange() {
    val saved = ConnectionForm.of(ConnectionTemplate.Anthropic)

    assertFalse(saved.sameAs(saved.copy(samplers = samplers(Sampler.Seed to "7"))))
  }

  @Test
  fun badJsonSaysWhatAndWhere() {
    val refused = assertIs<FormCheck.Refused>(
      ConnectionForm.of(ConnectionTemplate.OpenAi).copy(customBody = "{\"a\": }").check(),
    )

    assertEquals(FieldError.NotJson(JsonFault.ValueMissing, TextPosition(1, 7)), refused.errors[FormField.CustomBody])
  }

  @Test
  fun anUnreadableSamplerIsRefusedBeforeTheRepositorySeesIt() {
    val refused = assertIs<FormCheck.Refused>(
      ConnectionForm.of(ConnectionTemplate.OpenAi)
        .copy(samplers = samplers(Sampler.Seed to "1.5", Sampler.TopP to "x"))
        .check(),
    )

    assertEquals(FieldError.Sampler(SamplerError.NotWhole), refused.errors[FormField.SamplerField(Sampler.Seed)])
    assertEquals(FieldError.Sampler(SamplerError.NotANumber), refused.errors[FormField.SamplerField(Sampler.TopP)])
  }

  @Test
  fun aWaivedProblemDoesNotStopTheCheck() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(name = "", model = "")

    assertIs<FormCheck.Refused>(form.check())
    assertIs<FormCheck.Ready>(form.check(setOf(ConnectionProblem.NameEmpty, ConnectionProblem.ModelEmpty)))
  }

  @Test
  fun aHeaderProblemPointsAtItsRowPastBlankRows() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(
      headers = listOf(
        HeaderRow(4, "", null, ValueEdit.Entering),
        HeaderRow(9, "X Bad", null, ValueEdit.typing(SecretText("v"))),
      ),
    )

    val errors = form.errorsOf(setOf(ConnectionProblem.HeaderNameNotToken(0, ' '), ConnectionProblem.NameTaken))

    assertEquals(FieldError.HeaderNameBadCharacter(' '), errors[FormField.HeaderName(9)])
    assertEquals(FieldError.NameTaken("OpenAI"), errors[FormField.Name])
  }

  @Test
  fun aTypedKeyNeverShowsInTheFormsText() {
    val form = ConnectionForm.of(ConnectionTemplate.OpenAi).copy(key = typed("sk-secret"))

    assertFalse("sk-secret" in form.toString())
  }

  private fun ConnectionForm.readyDraft() = assertIs<FormCheck.Ready>(check()).draft
}

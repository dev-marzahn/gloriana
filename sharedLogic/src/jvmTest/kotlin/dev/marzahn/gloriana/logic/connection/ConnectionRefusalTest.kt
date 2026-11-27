package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlBadCharacter
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlBadPort
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlHasFragment
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlHasQuery
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlHasUserInfo
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlNoHost
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.BaseUrlNotHttp
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.ContextSizeNotPositive
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderContentTypeInvalid
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderNameEmpty
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderNameNotToken
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderNameRepeated
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderNameReserved
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderValueBadCharacter
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.HeaderValueGone
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.KeyBadCharacter
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.KeyBlank
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.MaxReplyTokensNotBelowContextSize
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.MaxReplyTokensNotPositive
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.ModelEmpty
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.NameEmpty
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.NameTaken
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.SamplerNotFinite
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.SamplerOutOfRange
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.StreamNotBoolean
import dev.marzahn.gloriana.logic.connection.ConnectionProblem.TimeoutNotPositive
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.logic.keys.header
import dev.marzahn.gloriana.logic.keys.opened
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConnectionRefusalTest {
  @TempDir
  lateinit var directory: Path

  private val library by lazy { ConnectionLibrary(directory) }

  @AfterEach
  fun closeLibrary() = library.close()

  @TestFactory
  fun eachBaseUrlFailureRefusesTheSaveStoresNothingAndMakesNoDataKey() = refusalTests(
    Refusal("no scheme", draft(baseUrl = "api.example.com/v1"), BaseUrlNotHttp),
    Refusal("ftp", draft(baseUrl = "ftp://api.example.com"), BaseUrlNotHttp),
    Refusal("no host", draft(baseUrl = "https:///v1"), BaseUrlNoHost),
    Refusal("a space in the host", draft(baseUrl = "https://api example.com"), BaseUrlBadCharacter, BaseUrlNoHost),
    Refusal("a backslash before @", draft(baseUrl = "https://good.example\\@evil.example/v1"),
      BaseUrlBadCharacter, BaseUrlHasUserInfo),
    Refusal("a user and password", draft(baseUrl = "https://user:secret@host.example/v1"), BaseUrlHasUserInfo),
    Refusal("port 0", draft(baseUrl = "http://host:0"), BaseUrlBadPort),
    Refusal("port 99999", draft(baseUrl = "https://host:99999/v1"), BaseUrlBadPort),
    Refusal("an empty port", draft(baseUrl = "https://host:/v1"), BaseUrlBadPort),
    Refusal("a malformed IPv6 address", draft(baseUrl = "http://[::::]/v1"), BaseUrlNoHost),
    Refusal("a port with a plus sign", draft(baseUrl = "https://host:+80/v1"), BaseUrlBadPort),
    Refusal("a port in Arabic-Indic digits", draft(baseUrl = "https://host:٨٠/v1"), BaseUrlBadPort),
    Refusal("only a scheme", draft(baseUrl = "https://"), BaseUrlNoHost),
    Refusal("a dot for a host", draft(baseUrl = "https://./v1"), BaseUrlNoHost),
    Refusal("a host of dashes", draft(baseUrl = "https://-/v1"), BaseUrlNoHost),
    Refusal("two trailing dots", draft(baseUrl = "https://example.com../v1"), BaseUrlNoHost),
    Refusal("full-width digits in the host", draft(baseUrl = "https://１２７.0.0.1/v1"), BaseUrlNoHost),
    Refusal("Arabic-Indic digits in the host", draft(baseUrl = "https://١٢٣.example"), BaseUrlNoHost),
    Refusal("a quote in the path", draft(baseUrl = "https://host/v\"1"), BaseUrlBadCharacter),
    Refusal("angle brackets in the path", draft(baseUrl = "https://host/<v1>"), BaseUrlBadCharacter),
    Refusal("a control character in the path", draft(baseUrl = "https://host/v\u00071"), BaseUrlBadCharacter),
    Refusal("a wrong scheme hides nothing else", draft(baseUrl = "ftp://u@h:0"),
      BaseUrlNotHttp, BaseUrlHasUserInfo, BaseUrlBadPort),
    Refusal("a query", draft(baseUrl = "https://api.example.com/v1?x=1"), BaseUrlHasQuery),
    Refusal("a fragment", draft(baseUrl = "https://api.example.com/v1#top"), BaseUrlHasFragment),
    Refusal("every base URL problem at once", draft(baseUrl = "https://u@host:0/v1?x#y"),
      BaseUrlHasUserInfo, BaseUrlBadPort, BaseUrlHasQuery, BaseUrlHasFragment),
  )

  @TestFactory
  fun eachOtherFieldFailureRefusesTheSaveStoresNothingAndMakesNoDataKey() = refusalTests(
    Refusal("an empty name", draft(name = "  "), NameEmpty),
    Refusal("an empty model", draft(model = " "), ModelEmpty),
    Refusal("an empty header name", draft(headers = listOf(HeaderDraft("", entered("v")))), HeaderNameEmpty(0)),
    Refusal("a header name outside token characters", draft(headers = listOf(HeaderDraft("X Org", entered("v")))),
      HeaderNameNotToken(0, ' ')),
    Refusal("two header names alike but for case",
      draft(headers = listOf(HeaderDraft("X-Org", entered("a")), HeaderDraft("x-org", entered("b")))),
      HeaderNameRepeated(1)),
    Refusal("CR in a header value", draft(headers = listOf(HeaderDraft("X-Org", entered("a\rb")))),
      HeaderValueBadCharacter(0, '\r')),
    Refusal("LF in a header value", draft(headers = listOf(HeaderDraft("X-Org", entered("a\nb")))),
      HeaderValueBadCharacter(0, '\n')),
    Refusal("a non-ASCII header value", draft(headers = listOf(HeaderDraft("X-Org", entered("café")))),
      HeaderValueBadCharacter(0, 'é')),
    Refusal("a control character in a header value", draft(headers = listOf(HeaderDraft("X-Org", entered("a\u0000")))),
      HeaderValueBadCharacter(0, '\u0000')),
    Refusal("a header the app sets itself", draft(headers = listOf(HeaderDraft("Accept-Encoding", entered("br")))),
      HeaderNameReserved(0)),
    Refusal("a Host header", draft(headers = listOf(HeaderDraft("host", entered("example.com")))),
      HeaderNameReserved(0)),
    Refusal("a malformed Content-Type", draft(headers = listOf(HeaderDraft("Content-Type", entered("json")))),
      HeaderContentTypeInvalid(0)),
    Refusal("a kept value no header holds",
      draft(headers = listOf(HeaderDraft("X-Org", HeaderValueInput.Kept("X-Gone")))), HeaderValueGone(0)),
    Refusal("a context size of 0", numbers(contextSize = 0), ContextSizeNotPositive),
    Refusal("negative maximum reply tokens", numbers(maxReplyTokens = -1), MaxReplyTokensNotPositive),
    Refusal("maximum reply tokens equal to the context size", numbers(contextSize = 1_024, maxReplyTokens = 1_024),
      MaxReplyTokensNotBelowContextSize),
    Refusal("maximum reply tokens above the context size", numbers(contextSize = 1_000, maxReplyTokens = 2_000),
      MaxReplyTokensNotBelowContextSize),
    Refusal("a timeout of 0", numbers(timeoutSeconds = 0), TimeoutNotPositive),
    Refusal("a temperature that is not a number", draft(samplers = Samplers(temperature = Double.NaN)),
      SamplerOutOfRange(Sampler.Temperature, SamplerRange.Decimal(0.0..2.0))),
    Refusal("an infinite penalty", draft(samplers = Samplers(presencePenalty = Double.POSITIVE_INFINITY)),
      SamplerOutOfRange(Sampler.PresencePenalty, SamplerRange.Decimal(-2.0..2.0))),
    Refusal("a temperature above the format's range", draft(samplers = Samplers(temperature = 2.5)),
      SamplerOutOfRange(Sampler.Temperature, SamplerRange.Decimal(0.0..2.0))),
    Refusal("a non-finite sampler the format doesn't show",
      draft(samplers = Samplers(frequencyPenalty = Double.NaN), format = ApiFormat.AnthropicMessages),
      SamplerNotFinite(Sampler.FrequencyPenalty)),
  )

  private fun refusalTests(vararg refusals: Refusal) = refusals.map { refusal ->
    DynamicTest.dynamicTest(refusal.case) {
      ConnectionLibrary(Files.createTempDirectory(directory, "refusal")).use { library ->
        runTest {
          val result = library.repository.create(refusal.draft.copy(key = KeyInput.Entered("sk-refused")))

          assertEquals(refusal.problems, assertIs<ConnectionSaveResult.Refused>(result).problems)
          assertEquals(emptyList(), library.list())
          assertNull(library.default())
          assertFalse(Files.exists(library.dataKeyFile()))
        }
      }
    }
  }

  @Test
  fun aCustomBodyStreamThatIsNoBooleanIsRefused() = runTest {
    listOf("""{"stream":"true"}""", """{"stream":1}""", """{"stream":{}}""").forEach { body ->
      val result = library.repository.create(draftWithBody(body))

      assertEquals(setOf(StreamNotBoolean), assertIs<ConnectionSaveResult.Refused>(result).problems, body)
    }
    assertIs<ConnectionSaveResult.Saved>(library.repository.create(draftWithBody("""{"stream":null}""")))
  }

  private fun draftWithBody(body: String) =
    draft().let { it.copy(fields = it.fields.copy(customBody = Json.parseToJsonElement(body).jsonObject)) }

  @Test
  fun aBlankKeyIsRefused() = runTest {
    listOf("", " ", " \t ").forEach { key ->
      val result = library.repository.create(draft(key = KeyInput.Entered(key)))

      assertEquals(setOf(KeyBlank), assertIs<ConnectionSaveResult.Refused>(result).problems, "'$key'")
    }
    assertEquals(emptyList(), library.list())
  }

  @Test
  fun aPaddedKeyAndHeaderValueAreStoredAsTheyGoOut() = runTest {
    val saved = library.created(
      draft(key = KeyInput.Entered(" sk-padded\t"), headers = listOf(HeaderDraft("X-Org", entered("\torg-1  ")))),
    )

    assertEquals("sk-padded", library.vault.opened(SecretSlot.ApiKey(saved.id), checkNotNull(saved.key)))
    assertEquals("org-1", library.vault.opened(header(saved.id, "X-Org"), saved.headers.single().value))
  }

  @Test
  fun aKeyHttpCannotCarryIsRefused() = runTest {
    mapOf("café" to 'é', "sk-1\n" to '\n', "sk\u0000" to '\u0000').forEach { (key, character) ->
      val result = library.repository.create(draft(key = KeyInput.Entered(key)))

      assertEquals(setOf(KeyBadCharacter(character)), assertIs<ConnectionSaveResult.Refused>(result).problems, key)
    }
    assertEquals(emptyList(), library.list())
  }

  @Test
  fun everyProblemIsReportedAtOnce() = runTest {
    val result = library.repository.create(draft(name = "", model = "", baseUrl = "ftp://x"))

    assertEquals(setOf(NameEmpty, ModelEmpty, BaseUrlNotHttp), assertIs<ConnectionSaveResult.Refused>(result).problems)
  }

  @Test
  fun aNameAnotherConnectionHasLetterCaseAsideIsTakenAndMakesNoDataKey() = runTest {
    val stored = library.created(draft(name = "Local"))

    val result = library.repository.create(draft(name = "LOCAL", key = KeyInput.Entered("sk-taken")))

    assertEquals(setOf(NameTaken), assertIs<ConnectionSaveResult.Refused>(result).problems)
    assertFalse(Files.exists(library.dataKeyFile()))
    assertIs<ConnectionSaveResult.Saved>(library.repository.update(stored.id, draft(name = "local")))
  }

  @Test
  fun addressesWithAHostSave() = runTest {
    listOf(
      "https://api.example.com/v1",
      "HTTP://localhost:5001",
      "http://192.168.1.4:8080/v1",
      "http://[::1]:11434/v1",
      "http://[2001:db8::7]/v1",
      "http://[::ffff:192.168.1.4]:8080/v1",
      "https://bücher.example/v1",
      "https://bücher.example/v1",
      "https://example.com./v1",
      "https://localhost.",
      "https://host:65535",
    ).forEachIndexed { index, url ->
      assertIs<ConnectionSaveResult.Saved>(library.repository.create(draft(name = "Host $index", baseUrl = url)), url)
    }
  }

  @Test
  fun customBodyTextMustBeAJsonObject() {
    assertEquals(CustomBodyText.NotAnObject, CustomBodyText.parse("[1]"))
    assertEquals(
      CustomBodyText.NotJson(TextPosition(1, 7), JsonFault.ValueMissing),
      CustomBodyText.parse("""{"a": }"""),
    )
    assertEquals(CustomBodyText.Object(JsonObject(emptyMap())), CustomBodyText.parse(" "))
    assertIs<CustomBodyText.Object>(CustomBodyText.parse("""{"top_a": 0.2, "thinking": null}"""))
  }

  private fun numbers(contextSize: Int = 16_384, maxReplyTokens: Int = 1_024, timeoutSeconds: Int = 60) =
    draft().let { base ->
      base.copy(
        fields = base.fields.copy(
          contextSize = contextSize,
          maxReplyTokens = maxReplyTokens,
          timeoutSeconds = timeoutSeconds,
        ),
      )
    }

  private class Refusal(val case: String, val draft: ConnectionDraft, vararg problems: ConnectionProblem) {
    val problems = problems.toSet()
  }
}

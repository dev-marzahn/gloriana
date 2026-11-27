package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.DraftCheck
import dev.marzahn.gloriana.logic.connection.check
import dev.marzahn.gloriana.logic.json.parsedObject
import dev.marzahn.gloriana.logic.json.string
import dev.marzahn.gloriana.logic.keys.KeyVault
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal val testPrompt = listOf(PromptSection(SectionRole.User, "Reply with one short sentence."))

private val noListStatuses: Set<HttpStatusCode> =
  setOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed, HttpStatusCode.NotImplemented)

private val testWaives: Set<ConnectionProblem> = setOf(ConnectionProblem.NameEmpty)

private val modelListWaives: Set<ConnectionProblem> = testWaives + ConnectionProblem.ModelEmpty

internal class VaultConnectionProbe(
  private val vault: KeyVault,
  private val settings: GlobalModelSettings,
  private val replies: ModelReplies,
  private val host: HostCalls,
) : ConnectionProbe {
  override suspend fun test(draft: ConnectionDraft, stored: Connection?): ProbeResult<String> =
    probing(draft, stored, testWaives, ::testReply)

  override suspend fun models(draft: ConnectionDraft, stored: Connection?): ProbeResult<List<String>> =
    probing(draft, stored, modelListWaives, ::modelList)

  private suspend fun <T> probing(
    draft: ConnectionDraft,
    stored: Connection?,
    waived: Set<ConnectionProblem>,
    work: suspend (OpenedConnection) -> ProbeResult<T>,
  ): ProbeResult<T> = failingAs({ ProbeResult.Failed(failureOf(it)) }) {
    when (val check = draft.check(stored, waivedFieldProblems = waived)) {
      is DraftCheck.Refused -> ProbeResult.Refused(check.problems)
      is DraftCheck.Ready -> when (val opening = vault.openSecrets(check.sources)) {
        is SecretsOpening.Refused -> ProbeResult.Failed(opening.error)
        is SecretsOpening.Opened -> work(OpenedConnection(check.fields, opening.key, opening.headers))
      }
    }
  }

  private suspend fun testReply(connection: OpenedConnection): ProbeResult<String> {
    val events = replies.reply(testPrompt, connection, settings.currentSamplers()).toList()
    val failure = events.filterIsInstance<ModelEvent.Failed>().firstOrNull()
    return failure?.let { ProbeResult.Failed(it.error) }
      ?: ProbeResult.Done(events.filterIsInstance<ModelEvent.TextDelta>().joinToString("") { it.text })
  }

  private suspend fun modelList(connection: OpenedConnection): ProbeResult<List<String>> {
    val target = connection.fields.format.adapter.modelListTarget
    val request = hostRequest(connection, HttpMethod.Get, target, body = null)
      ?: return ProbeResult.Failed(ModelApiError.BadAddress)
    return when (val reply = host.whole(request)) {
      is HostReply.Body -> modelListOf(reply.text, request.scrubber)
      is HostReply.Failed -> modelListFailure(reply.error)
    }
  }
}

internal fun modelListFailure(error: ModelApiError): ProbeResult<List<String>> =
  if (error is ModelApiError.HostError && noListStatuses.any { it.value == error.status }) {
    ProbeResult.Done(emptyList())
  } else {
    ProbeResult.Failed(error)
  }

internal fun modelListOf(text: String, scrubber: Scrubber): ProbeResult<List<String>> {
  val entries = parsedObject(text)?.get("data") as? JsonArray
    ?: return ProbeResult.Failed(ModelApiError.HostError(status = null, body = scrubber.scrub(text)))
  val ids = entries.filterIsInstance<JsonObject>().mapNotNull { it.string("id")?.takeIf(String::isNotBlank) }
  return ProbeResult.Done(ids.distinct())
}

package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.app.ProblemLog
import dev.marzahn.gloriana.logic.characters.CharacterLookup
import dev.marzahn.gloriana.logic.chat.Chat
import dev.marzahn.gloriana.logic.chat.ChatLibrary
import dev.marzahn.gloriana.logic.chat.LeftoverDraftsAtStart
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.connection.api.ModelApi
import dev.marzahn.gloriana.logic.connection.api.SecretsCheck
import dev.marzahn.gloriana.logic.connection.api.StubHostReplies
import dev.marzahn.gloriana.logic.connection.api.TEST_KEY
import dev.marzahn.gloriana.logic.connection.api.VaultModelApi
import dev.marzahn.gloriana.logic.storage.DatabaseOpenedAtStart
import dev.marzahn.gloriana.logic.tasks.KeepAlive
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.TaskHold
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import dev.marzahn.gloriana.logic.tasks.TaskRunner
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

val writingNotice: Flow<TaskNotice> = flowOf(TaskNotice("Writing a reply", "", null))

class LosableKeepAlive : KeepAlive {
  @Volatile
  var losses: KeepAliveLosses? = null

  override fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses) {
    this.losses = losses
  }

  @Volatile
  var onStop: () -> Unit = {}

  override fun stop() = onStop()

  override fun holdWhileRunning() = TaskHold {}
}

internal class ReplyHarness(
  directory: Path,
  saveInterval: Duration = 100.milliseconds,
  modelApi: ((ModelApi) -> ModelApi) = { it },
) : AutoCloseable {
  val stub = StubHostReplies(directory, delay = 2.seconds)
  val library = ChatLibrary(directory.resolve("library"))
  val problems: MutableList<String> = CopyOnWriteArrayList()
  val keepAlive = LosableKeepAlive()
  private val log = ProblemLog { message, _ -> problems += message }
  private val scope = ApplicationScope { problems += "Escaped: $it" }
  private val connections = library.connections
  private val api = modelApi(VaultModelApi(connections.vault, GlobalModelSettings(connections.settings), stub.replies))
  private val live = LiveReplies()
  val writer = ReplyWriter(
    library.repository,
    ReplyPreparation(
      library.repository,
      CharacterLookup(library.database.characterDao()),
      library.personas.prompts,
      library.presets.repository,
      connections.repository,
      SecretsCheck(connections.vault),
    ),
    library.replyRows,
    LeftoverDraftsAtStart(library.leftoverDrafts, DatabaseOpenedAtStart(library.database, scope), scope),
    TaskRunner(scope, keepAlive),
    ReplyRun(library.repository, library.replyRows, ReplyStream(api, library.replyRows, log, saveInterval), live, log),
    live,
  )

  suspend fun chat(
    mode: String = "normal",
    format: ApiFormat = ApiFormat.OpenAiChatCompletions,
    streaming: Boolean = true,
    key: KeyInput = KeyInput.Entered(TEST_KEY),
    baseUrl: String = stub.host.baseUrl(mode),
    description: String = "",
  ): Chat {
    val made = draft(name = "Host ${Uuid.random()}", baseUrl = baseUrl, key = key, format = format)
    val fields = made.fields.copy(streaming = streaming, timeoutSeconds = 1)
    val connection = connections.created(made.copy(fields = fields))
    val character = library.character(description = description)
    return library.started(library.newChat(character).copy(connectionId = connection.id))
  }

  suspend fun sent(chatId: Uuid, text: String = "Hello, {{char}}."): Uuid =
    (writer.send(chatId, text, writingNotice) as ReplyStartResult.Started).messageId

  suspend fun settled(chatId: Uuid): LiveReply? = withTimeout(10.seconds) {
    writer.replies.first { it[chatId] !is LiveReply.Writing }[chatId]
  }

  override fun close() {
    scope.cancel()
    stub.close()
    library.close()
  }
}

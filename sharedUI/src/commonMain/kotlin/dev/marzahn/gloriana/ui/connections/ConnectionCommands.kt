package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDeleteResult
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.MakeDefaultResult
import dev.marzahn.gloriana.ui.lists.Deleted
import dev.marzahn.gloriana.ui.lists.LastDeleted
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_deleted
import dev.marzahn.gloriana.ui.resources.snack_made_default
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString

fun List<Connection>.inListOrder(): List<Connection> = sortedBy { it.fields.name.lowercase() }

enum class CommandResult { Done, Failed }

class ConnectionCommands(private val repository: ConnectionRepository, private val messages: AppMessages) {
  val deletions = LastDeleted()

  suspend fun makeDefault(connection: Connection): CommandResult = when (repository.makeDefault(connection.id)) {
    MakeDefaultResult.Made -> CommandResult.Done.also {
      messages.show(AppMessage(getString(Res.string.snack_made_default, connection.fields.name)))
    }
    MakeDefaultResult.Gone, MakeDefaultResult.Failed -> CommandResult.Failed
  }

  suspend fun delete(connection: Connection): CommandResult {
    val index = repository.connections().catch { emit(emptyList()) }.first().inListOrder()
      .indexOfFirst { it.id == connection.id }
    return when (repository.delete(connection.id)) {
      ConnectionDeleteResult.Deleted,
      ConnectionDeleteResult.DeletedKeyLeftForNextStart,
      ConnectionDeleteResult.Gone,
      -> {
        deletions.record(Deleted(connection.id, index.coerceAtLeast(0)))
        messages.show(AppMessage(getString(Res.string.snack_deleted, connection.fields.name)))
        CommandResult.Done
      }
      ConnectionDeleteResult.Failed -> CommandResult.Failed
    }
  }
}

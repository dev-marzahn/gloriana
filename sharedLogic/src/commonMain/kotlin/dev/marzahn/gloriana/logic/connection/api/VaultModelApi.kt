package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.secretSources
import dev.marzahn.gloriana.logic.keys.KeyVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal class VaultModelApi(
  private val vault: KeyVault,
  private val settings: GlobalModelSettings,
  private val replies: ModelReplies,
) : ModelApi {
  override fun reply(sections: List<PromptSection>, connection: Connection): Flow<ModelEvent> = flow {
    when (val opening = vault.openSecrets(connection.secretSources())) {
      is SecretsOpening.Refused -> emit(ModelEvent.Failed(opening.error))
      is SecretsOpening.Opened -> {
        val opened = OpenedConnection(connection.fields, opening.key, opening.headers)
        emitAll(replies.reply(sections, opened, settings.currentSamplers()))
      }
    }
  }.catch { cause ->
    if (cause !is Exception || cause is CancellationException) throw cause
    emit(ModelEvent.Failed(ModelApiError.Unexpected))
  }
}

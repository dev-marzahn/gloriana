package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.keys.HeaderName
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.SecretSlot
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
    val key = connection.key?.let { sealed ->
      vault.open(SecretSlot.ApiKey(connection.id), sealed).valueOr { return@flow emit(ModelEvent.Failed(it)) }
    }
    val headers = connection.headers.map { header ->
      val name = HeaderName.of(header.name)
      val value = vault.open(SecretSlot.HeaderValue(connection.id, name), header.value)
        .valueOr { return@flow emit(ModelEvent.Failed(it)) }
      HostHeader(name, value)
    }
    val opened = OpenedConnection(connection.fields, key, headers)
    emitAll(replies.reply(sections, opened, settings.currentSamplers()))
  }.catch { emit(ModelEvent.Failed(failureOf(it))) }
}

private inline fun OpenResult.valueOr(refuse: (ModelApiError) -> Nothing): String = when (this) {
  is OpenResult.Opened -> value
  OpenResult.Missing -> refuse(ModelApiError.KeysMissing)
  OpenResult.Locked -> refuse(ModelApiError.KeysLocked)
  OpenResult.Unavailable -> refuse(ModelApiError.KeysUnavailable)
}

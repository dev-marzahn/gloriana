package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.personas.storage.DefaultPersona
import dev.marzahn.gloriana.logic.personas.storage.PersonaDao
import dev.marzahn.gloriana.logic.personas.storage.PersonaWrites
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class PersonaRepository internal constructor(
  private val dao: PersonaDao,
  private val writes: PersonaWrites,
  private val default: DefaultPersona,
  private val chats: ChatsUsing,
) {
  fun personas(): Flow<List<Persona>> = dao.observeAll().map { rows -> rows.map { it.persona } }

  fun persona(id: Uuid): Flow<Persona?> = dao.observe(id).map { it?.persona }

  fun defaultPersona(): Flow<Uuid?> = default.observe()

  suspend fun chatsUsing(id: Uuid): Int = chats.count(ChatSetting.Persona, id)

  suspend fun create(fields: PersonaFields): PersonaSaveResult = saving { writes.insert(fields) }

  suspend fun update(id: Uuid, fields: PersonaFields): PersonaSaveResult = saving { writes.update(id, fields) }

  suspend fun delete(id: Uuid): PersonaDeleteResult = failingAs(PersonaDeleteResult.Failed) {
    default.unlessDefault(id, refused = PersonaDeleteResult.IsDefault) { writes.delete(id) }
  }

  suspend fun makeDefault(id: Uuid): MakeDefaultResult = failingAs(MakeDefaultResult.Failed) { default.make(id) }

  private suspend fun saving(write: suspend () -> PersonaSaveResult): PersonaSaveResult =
    failingAs({ if (it.isStorageFull()) PersonaSaveResult.StorageFull else PersonaSaveResult.Failed }, write)
}

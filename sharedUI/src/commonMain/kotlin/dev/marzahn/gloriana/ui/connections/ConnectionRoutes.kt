package dev.marzahn.gloriana.ui.connections

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.logic.connection.ConnectionTemplate
import dev.marzahn.gloriana.ui.navigation.Opening
import dev.marzahn.gloriana.ui.state.DraftEdit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.uuid.Uuid

@Serializable
sealed interface NewConnectionStart {
  @Serializable
  data class FromTemplate(val template: ConnectionTemplate) : NewConnectionStart

  @Serializable
  data object FromNothing : NewConnectionStart

  companion object {
    val choices: List<NewConnectionStart> = ConnectionTemplate.entries.map(::FromTemplate) + FromNothing
  }
}

@Serializable
sealed interface ConnectionRoute : NavKey {
  @Serializable
  data class Saved(val id: Uuid) : ConnectionRoute {
    internal constructor(id: Uuid, editsDuringItsFirstSave: List<DraftEdit<ConnectionForm>>) : this(id) {
      this.editsDuringItsFirstSave = editsDuringItsFirstSave
    }

    @Transient
    internal var editsDuringItsFirstSave: List<DraftEdit<ConnectionForm>> = emptyList()
      private set
  }

  @Serializable
  data class New(val start: NewConnectionStart, val opening: Opening) : ConnectionRoute
}

@Serializable
data object ModelSettingsRoute : NavKey

@Serializable
data object PassphraseRoute : NavKey

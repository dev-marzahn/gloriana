package dev.marzahn.gloriana.ui.connections

import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.logic.connection.ConnectionTemplate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
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
  data class Saved(val id: String) : ConnectionRoute {
    constructor(id: Uuid) : this(id.toString())

    val uuid: Uuid get() = Uuid.parse(id)
  }

  @Serializable
  data class New(val start: NewConnectionStart, val opening: Opening) : ConnectionRoute
}

@Serializable
@JvmInline
value class Opening(val id: String) {
  companion object {
    fun fresh() = Opening(Uuid.random().toString())
  }
}

@Serializable
data object ModelSettingsRoute : NavKey

@Serializable
data object PassphraseRoute : NavKey

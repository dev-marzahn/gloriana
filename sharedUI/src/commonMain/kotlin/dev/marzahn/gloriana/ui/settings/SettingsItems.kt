package dev.marzahn.gloriana.ui.settings

import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.settings_group_connections
import dev.marzahn.gloriana.ui.resources.settings_group_every_connection
import dev.marzahn.gloriana.ui.resources.settings_group_keys
import org.jetbrains.compose.resources.StringResource
import kotlin.uuid.Uuid

internal sealed interface SettingsItem {
  val key: String

  data class Group(val title: StringResource) : SettingsItem {
    override val key = "group-$title"
  }

  data object Notes : SettingsItem {
    override val key = "notes"
  }

  data object Loading : SettingsItem {
    override val key = "loading"
  }

  data object LoadFailed : SettingsItem {
    override val key = "load-failed"
  }

  data object Add : SettingsItem {
    override val key = "add"
  }

  data object Empty : SettingsItem {
    override val key = "empty"
  }

  data object ModelSettings : SettingsItem {
    override val key = "model-settings"
  }

  data object Passphrase : SettingsItem {
    override val key = "passphrase"
  }
}

internal fun connectionKey(id: Uuid) = "connection-$id"

internal class SettingsRows(state: SettingsState, inPane: Boolean, reordering: Boolean) {
  val connections = (state.connections as? ConnectionsList.Loaded)?.rows.orEmpty()

  val before: List<SettingsItem> = buildList {
    add(SettingsItem.Group(Res.string.settings_group_connections))
    add(SettingsItem.Notes)
    when (state.connections) {
      ConnectionsList.Loading -> add(SettingsItem.Loading)
      ConnectionsList.CouldNotLoad -> add(SettingsItem.LoadFailed)
      is ConnectionsList.Loaded -> Unit
    }
  }

  val after: List<SettingsItem> = buildList {
    if (reordering) return@buildList
    (state.connections as? ConnectionsList.Loaded)?.let { list ->
      add(if (list.rows.isNotEmpty() || inPane) SettingsItem.Add else SettingsItem.Empty)
    }
    add(SettingsItem.Group(Res.string.settings_group_every_connection))
    add(SettingsItem.ModelSettings)
    if (state.showsPassphrase) {
      add(SettingsItem.Group(Res.string.settings_group_keys))
      add(SettingsItem.Passphrase)
    }
  }

  val focusAfterDelete: String? = (state.focus as? ListFocus.Row)?.let { focused ->
    connections.getOrNull(focused.index)?.let { connectionKey(it.id) }
      ?: after.firstOrNull { it == SettingsItem.Add || it == SettingsItem.Empty }?.key
  }
}

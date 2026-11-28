package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.Sampler

class ModelSettingsActions(
  val onBack: () -> Unit,
  val onRetry: () -> Unit,
  val onEdit: (Sampler, String) -> Unit,
  val onSave: () -> Unit,
)

package dev.marzahn.gloriana.ui.presets

class PresetPageActions(
  val onBack: () -> Unit,
  val onIntent: (PresetIntent) -> Unit,
  val onOpen: (PresetPart) -> Unit,
  val onExport: () -> Unit,
)

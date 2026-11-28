package dev.marzahn.gloriana.ui.personas

class PersonaPageActions(
  val onBack: () -> Unit = {},
  val onIntent: (PersonaIntent) -> Unit = {},
  val onCancel: () -> Unit = {},
)

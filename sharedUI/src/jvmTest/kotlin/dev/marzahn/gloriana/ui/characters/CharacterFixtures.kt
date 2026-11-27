package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterFields
import dev.marzahn.gloriana.logic.characters.KeptCardFields
import dev.marzahn.gloriana.logic.characters.Pronouns
import kotlin.uuid.Uuid

val CharacterIds = List(10) { Uuid.random() }

fun character(
  fields: CharacterFields = CharacterFields(name = "Tamsin"),
  pronouns: Pronouns = Pronouns.None,
  kept: KeptCardFields = KeptCardFields(),
  id: Uuid = CharacterIds[0],
) = Character(
  id = id,
  importedAt = 1L,
  fields = fields,
  avatar = null,
  assets = emptyList(),
  pronouns = pronouns,
  keptFields = kept,
)

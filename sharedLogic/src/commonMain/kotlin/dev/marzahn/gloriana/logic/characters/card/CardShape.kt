package dev.marzahn.gloriana.logic.characters.card

import dev.marzahn.gloriana.logic.json.holds
import dev.marzahn.gloriana.logic.json.objectAt
import dev.marzahn.gloriana.logic.json.string
import kotlinx.serialization.json.JsonObject

private const val SPEC_KEY = "spec"
private const val DATA_KEY = "data"

internal sealed interface CardShape {
  val root: JsonObject
  val fields: JsonObject

  class Ccv1(override val root: JsonObject) : CardShape {
    override val fields: JsonObject = root
  }

  class WithData(override val root: JsonObject, override val fields: JsonObject) : CardShape

  companion object {
    fun of(root: JsonObject): CardShape {
      val data = root.objectAt(DATA_KEY)
        ?.takeIf { root.holds(SPEC_KEY) && it.string(CardTextField.Name.key) != null }
      return if (data != null) WithData(root, data) else Ccv1(root)
    }
  }
}

internal fun JsonObject.hasKnownSpec() = string(SPEC_KEY) in setOf(SPEC_V2, SPEC_V3)


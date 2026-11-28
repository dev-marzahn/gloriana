package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.json.NoFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

data class Character(
  val id: Uuid,
  val importedAt: Long,
  val fields: CharacterFields,
  val avatar: AssetHash?,
  val assets: List<CharacterAsset>,
  val pronouns: Pronouns,
  val keptFields: KeptCardFields,
) {
  val leftOutOfJson: List<LeftOutOfJson>
    get() = listOfNotNull(avatar?.let { LeftOutOfJson.Avatar }) +
      assets.filter { it.source is AssetSource.Stored }.map(LeftOutOfJson::Asset)
}

data class CharacterSummary(val id: Uuid, val name: String, val avatar: AssetHash?)

data class CharacterFields(
  val name: String,
  val nickname: String = "",
  val description: String = "",
  val personality: String = "",
  val scenario: String = "",
  val firstMessage: String = "",
  val alternateGreetings: List<String> = emptyList(),
  val groupOnlyGreetings: List<String> = emptyList(),
  val exampleMessages: String = "",
  val creator: String = "",
  val characterVersion: String = "",
  val creatorNotes: String = "",
  val systemPrompt: String = "",
  val postHistoryInstructions: String = "",
  val characterNote: CharacterNote? = null,
)

data class CharacterNote(val text: String, val depth: Int, val role: String)

data class Pronouns(
  val subject: String? = null,
  val objective: String? = null,
  val possessive: String? = null,
  val possessivePronoun: String? = null,
  val reflexive: String? = null,
) {
  val isEmpty: Boolean
    get() = listOfNotNull(subject, objective, possessive, possessivePronoun, reflexive).isEmpty()

  companion object {
    val None = Pronouns()
  }
}

data class CharacterAsset(
  val type: String,
  val name: String,
  val extension: String,
  val source: AssetSource,
  val otherKeys: JsonObject = NoFields,
)

sealed interface AssetSource {
  data class Stored(val hash: AssetHash) : AssetSource

  data class Uri(val uri: String) : AssetSource

  data object Unnamed : AssetSource
}

@Serializable
data class KeptCardFields(
  val tags: List<String> = emptyList(),
  val source: List<String> = emptyList(),
  val creationDate: Long? = null,
  val modificationDate: Long? = null,
  val creatorNotesInOtherLanguages: Map<String, String> = emptyMap(),
  val hasEmbeddedLorebook: Boolean = false,
)

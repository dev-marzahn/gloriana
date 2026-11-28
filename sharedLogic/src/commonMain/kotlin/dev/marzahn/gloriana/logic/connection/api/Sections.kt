package dev.marzahn.gloriana.logic.connection.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val SECTION_JOIN = "\n\n"

internal fun leadingSystemText(sections: List<PromptSection>): String? = sections
  .takeWhile { it.role == SectionRole.System }
  .takeIf { it.isNotEmpty() }
  ?.joinToString(SECTION_JOIN) { it.text }

internal fun afterLeadingSystem(sections: List<PromptSection>): List<PromptSection> =
  sections.dropWhile { it.role == SectionRole.System }

internal val SectionRole.wireName: String
  get() = when (this) {
    SectionRole.System -> "system"
    SectionRole.User -> "user"
    SectionRole.Assistant -> "assistant"
  }

internal fun roleMessages(sections: List<PromptSection>): JsonArray = buildJsonArray {
  sections.forEach { section ->
    add(
      buildJsonObject {
        put("role", section.role.wireName)
        put("content", section.text)
      },
    )
  }
}

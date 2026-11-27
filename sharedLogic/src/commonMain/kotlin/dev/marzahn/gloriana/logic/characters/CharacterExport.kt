package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.files.ExportProblem

enum class CardFormat {
  Png,
  Json,
}

sealed interface ExportResult {
  data class Saved(val avatarChange: AvatarChange?) : ExportResult

  data class NotSaved(val problem: ExportProblem) : ExportResult

  data object CharacterGone : ExportResult
}

sealed interface AvatarChange {
  data class Converted(val firstFrameOnly: Boolean, val downsampled: Boolean) : AvatarChange

  data object Unreadable : AvatarChange
}

sealed interface LeftOutOfJson {
  data object Avatar : LeftOutOfJson

  data class Asset(val asset: CharacterAsset) : LeftOutOfJson
}

sealed interface DeleteResult {
  data object Deleted : DeleteResult

  data object Gone : DeleteResult

  data object Failed : DeleteResult
}

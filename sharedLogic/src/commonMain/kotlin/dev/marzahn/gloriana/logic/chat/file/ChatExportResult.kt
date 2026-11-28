package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.files.ExportProblem

sealed interface ChatExportResult {
  data object Exported : ChatExportResult

  data object Gone : ChatExportResult

  data object Unreadable : ChatExportResult

  data class Failed(val problem: ExportProblem) : ChatExportResult
}

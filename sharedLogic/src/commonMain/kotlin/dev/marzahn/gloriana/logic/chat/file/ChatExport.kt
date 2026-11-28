package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportWrite
import dev.marzahn.gloriana.logic.files.writeOnIo
import kotlin.uuid.Uuid

class ChatExport internal constructor(private val reads: ChatFileReads) {
  suspend fun export(chatId: Uuid, destination: ExportDestination): ChatExportResult =
    when (val read = reads.read(chatId)) {
      ChatFileRead.Gone -> ChatExportResult.Gone
      ChatFileRead.Unreadable -> ChatExportResult.Unreadable
      is ChatFileRead.Read -> written(destination.writeOnIo { sink -> ChatJsonlWriter.write(read.file, sink) })
    }

  private fun written(write: ExportWrite): ChatExportResult = when (write) {
    ExportWrite.Written -> ChatExportResult.Exported
    is ExportWrite.Failed -> ChatExportResult.Failed(write.problem)
  }
}

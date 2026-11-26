package dev.marzahn.gloriana.stubhost

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

internal sealed interface Launch {
  data class Start(val options: LaunchOptions, val record: RequestRecord) : Launch

  data object ShowHelp : Launch

  data class Exit(val reason: ExitReason, val message: String) : Launch

  enum class ExitReason(val exitCode: Int) {
    INVALID_OPTIONS(2),
    RECORD_UNWRITABLE(1),
  }

  companion object {
    fun from(args: List<String>, temporaryRecord: () -> Path = ::temporaryRecordFile): Launch {
      if (args.any { it in LaunchOptions.helpFlags }) return ShowHelp
      return try {
        start(LaunchOptions.parse(args), temporaryRecord)
      } catch (invalid: IllegalArgumentException) {
        Exit(ExitReason.INVALID_OPTIONS, "${invalid.message}\n\n${LaunchOptions.help}")
      }
    }

    private fun start(options: LaunchOptions, temporaryRecord: () -> Path): Launch {
      val named = options.recordFile
      return try {
        Start(options, RequestRecord.open(named ?: temporaryRecord()))
      } catch (unwritable: IOException) {
        val file = named?.toAbsolutePath()?.toString() ?: "a temporary record file"
        Exit(ExitReason.RECORD_UNWRITABLE, "Can't write $file: ${plainReason(named, unwritable)}.")
      }
    }

    private fun plainReason(file: Path?, failure: IOException): String = when {
      file != null && Files.isDirectory(file) -> "it is a folder"
      failure is NoSuchFileException -> "its folder doesn't exist"
      failure is AccessDeniedException -> "permission denied"
      else -> failure.message ?: failure.javaClass.simpleName
    }

    private fun temporaryRecordFile(): Path = Files.createTempFile("stub-host-record-", ".jsonl")
  }
}

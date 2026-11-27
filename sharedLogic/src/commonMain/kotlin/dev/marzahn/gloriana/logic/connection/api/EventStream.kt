package dev.marzahn.gloriana.logic.connection.api

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.LineEnding
import io.ktor.utils.io.readLine

private const val DATA_FIELD = "data"
private const val COLON = ':'
private const val BYTE_ORDER_MARK_CODE = 0xFEFF
private val byteOrderMark = Char(BYTE_ORDER_MARK_CODE).toString()

internal suspend fun ByteReadChannel.readEventData(onData: suspend (String) -> Unit) {
  val data = mutableListOf<String>()
  var line = nextLine()?.removePrefix(byteOrderMark) ?: return
  while (true) {
    when {
      line.isEmpty() -> {
        if (data.isNotEmpty()) onData(data.joinToString("\n"))
        data.clear()
      }
      line.startsWith(COLON) -> Unit
      line.substringBefore(COLON) == DATA_FIELD -> data += line.substringAfter(COLON, "").removePrefix(" ")
    }
    line = nextLine() ?: return
  }
}

private suspend fun ByteReadChannel.nextLine(): String? = readLine(LineEnding.Lenient)

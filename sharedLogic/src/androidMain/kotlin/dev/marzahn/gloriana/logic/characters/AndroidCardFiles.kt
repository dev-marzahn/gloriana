package dev.marzahn.gloriana.logic.characters

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.characters.files.asByteSink
import dev.marzahn.gloriana.logic.characters.files.asByteSource
import java.io.FileNotFoundException
import java.io.IOException

class AndroidPickedFile(private val resolver: ContentResolver, private val uri: Uri) : PickedFile {
  override val name: String by lazy { displayName() ?: uri.lastPathSegment.orEmpty() }

  override fun open(): OpenedFile {
    val input = try {
      resolver.openInputStream(uri)
    } catch (_: FileNotFoundException) {
      null
    } catch (_: SecurityException) {
      null
    }
    return input?.let { OpenedFile.Opened(it.asByteSource()) } ?: OpenedFile.Unavailable
  }

  private fun displayName(): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }
  }.getOrNull()
}

class AndroidExportDestination(private val resolver: ContentResolver, private val uri: Uri) : ExportDestination {
  override fun write(content: (ByteSink) -> Unit): ExportWrite = try {
    val output = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
    output.use { content(it.asByteSink()) }
    ExportWrite.Written
  } catch (error: IOException) {
    failed(if (error.isStorageFull()) ExportFailure.StorageFull else ExportFailure.CantWrite)
  } catch (_: SecurityException) {
    failed(ExportFailure.CantWrite)
  } catch (failure: ExportContentFailure) {
    failed(failure.reason)
  }

  private fun failed(reason: ExportFailure) =
    ExportWrite.Failed(ExportProblem(reason, incompleteFileLeft = !deleteIncompleteFile()))

  private fun deleteIncompleteFile(): Boolean =
    runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
}

package dev.marzahn.gloriana.logic.storage

import androidx.room.Room
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path

fun desktopGlorianaDatabase(dataDirectory: Path, queries: CoroutineDispatcher = Dispatchers.IO): GlorianaDatabase {
  Files.createDirectories(dataDirectory)
  return Room.databaseBuilder<GlorianaDatabase>(
    name = dataDirectory.resolve(DATABASE_FILE_NAME).toString(),
    factory = ::GlorianaDatabase_Impl,
  ).buildGlorianaDatabase(queries)
}

package dev.marzahn.gloriana.logic.storage

import androidx.room.Room
import java.nio.file.Files
import java.nio.file.Path

fun desktopGlorianaDatabase(dataDirectory: Path): GlorianaDatabase {
  Files.createDirectories(dataDirectory)
  return Room.databaseBuilder<GlorianaDatabase>(
    name = dataDirectory.resolve(DATABASE_FILE_NAME).toString(),
    factory = ::GlorianaDatabase_Impl,
  ).buildGlorianaDatabase()
}

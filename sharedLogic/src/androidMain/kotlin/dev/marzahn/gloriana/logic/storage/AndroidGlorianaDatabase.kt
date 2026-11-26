package dev.marzahn.gloriana.logic.storage

import android.content.Context
import androidx.room.Room

fun androidGlorianaDatabase(context: Context): GlorianaDatabase =
  Room.databaseBuilder<GlorianaDatabase>(
    context = context,
    name = context.filesDir.resolve(DATABASE_FILE_NAME).absolutePath,
    factory = ::GlorianaDatabase_Impl,
  ).buildGlorianaDatabase()

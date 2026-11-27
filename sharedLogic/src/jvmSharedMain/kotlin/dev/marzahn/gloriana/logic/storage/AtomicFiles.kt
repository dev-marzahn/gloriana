package dev.marzahn.gloriana.logic.storage

import java.io.IOException
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private const val PARTIAL_SUFFIX = ".part"
private val directoriesOpenForSync = !System.getProperty("os.name").orEmpty().startsWith("Windows")

fun Path.writeAtomically(staged: () -> Unit = {}, content: (OutputStream) -> Unit) {
  val partial = Files.createTempFile(toAbsolutePath().parent, ".$fileName", PARTIAL_SUFFIX)
  try {
    FileChannel.open(partial, StandardOpenOption.WRITE).use { channel ->
      Channels.newOutputStream(channel).buffered().let { stream ->
        content(stream)
        stream.flush()
      }
      channel.force(true)
    }
    staged()
    partial.moveDurably(this, StandardCopyOption.REPLACE_EXISTING)
  } finally {
    deleteLeftover(partial)
  }
}

fun Path.moveDurably(target: Path, vararg options: StandardCopyOption) {
  Files.move(this, target, StandardCopyOption.ATOMIC_MOVE, *options)
  syncDirectoryAfterTheFact(target.toAbsolutePath().parent)
}

fun Path.createDirectoriesDurably() {
  val missing = generateSequence(toAbsolutePath()) { it.parent }.takeWhile { !Files.isDirectory(it) }.toList()
  Files.createDirectories(this)
  missing.reversed().forEach { syncDirectoryAfterTheFact(it.parent) }
}

fun Path.removePartialWrites() {
  if (!Files.isDirectory(this)) return
  listDirectoryEntries()
    .filter { it.name.startsWith(".") && it.name.endsWith(PARTIAL_SUFFIX) }
    .forEach(::deleteLeftover)
}

private fun syncDirectoryAfterTheFact(directory: Path) {
  if (!directoriesOpenForSync) return
  try {
    FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
  } catch (_: IOException) {
    return
  }
}

private fun deleteLeftover(file: Path) {
  try {
    Files.deleteIfExists(file)
  } catch (_: IOException) {
    return
  }
}

package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.assets.AssetWriteLock
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.characters.files.JvmCardReader
import dev.marzahn.gloriana.logic.characters.files.JvmCardWriter
import dev.marzahn.gloriana.logic.characters.storage.CharacterWrites
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.TestClock
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.uuid.Uuid

class CharacterLibrary(
  val directory: Path,
  transcoder: AvatarTranscoder = AvatarTranscoder { _, _ -> null },
  faults: CharacterWriteFaults = CharacterWriteFaults.None,
  hold: ImportHold = ImportHold.None,
) : AutoCloseable {
  val clock = TestClock(epochMillis = 1_000)
  val database: GlorianaDatabase = desktopGlorianaDatabase(directory)
  internal val updatedAt = UpdatedAtClock(clock)
  internal val assets = AssetStore(LibraryDirectory { directory })
  internal val lock = AssetWriteLock()
  val repository = CharacterRepository(
    dao = database.characterDao(),
    writes = CharacterWrites(database, UuidV7IdFactory, updatedAt, Tombstones(database, updatedAt), faults),
    reader = JvmCardReader(assets),
    writer = JvmCardWriter(assets, transcoder),
    hold = hold,
    assetLock = lock,
  )

  suspend fun importOne(
    file: PickedFile,
    choice: AlreadyInLibraryChoice = AlreadyInLibraryChoice.Copy,
  ): FileImportResult = repository.import(listOf(file), { AlreadyInLibraryAnswer(choice) }).single()

  suspend fun importedCharacter(file: PickedFile): Character {
    val result = importOne(file)
    check(result is FileImportResult.Imported) { "${file.name} did not import: $result" }
    return character(result.id)
  }

  suspend fun character(id: Uuid): Character = checkNotNull(repository.character(id).first())

  suspend fun list(): List<CharacterSummary> = repository.characters().first()

  suspend fun export(id: Uuid, format: CardFormat): Pair<ExportResult, ByteArray> {
    val destination = MemoryDestination()
    return repository.export(id, format, destination) to destination.bytes
  }

  fun storedFiles(): Set<String> = directory.resolve("assets").toFile().walkTopDown()
    .filter { it.isFile && it.parentFile.name != "staging" }.map { it.name }.toSet()

  fun stagingFiles(): List<String> =
    directory.resolve("assets").resolve("staging").toFile().listFiles().orEmpty().map { it.name }

  override fun close() = database.close()
}

class MemoryDestination : ExportDestination {
  var bytes = ByteArray(0)
    private set

  override fun write(content: (ByteSink) -> Unit): ExportWrite {
    val out = ByteArrayOutputStream()
    content { bytes, offset, length -> out.write(bytes, offset, length) }
    bytes = out.toByteArray()
    return ExportWrite.Written
  }
}

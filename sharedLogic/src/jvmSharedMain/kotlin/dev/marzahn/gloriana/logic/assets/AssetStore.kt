package dev.marzahn.gloriana.logic.assets

import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private const val ASSETS_DIRECTORY = "assets"
private const val STAGING_DIRECTORY = "staging"
private const val FAN_OUT_LENGTH = 2
private val HASH_NAME = Regex("[0-9a-f]{64}")

internal class AssetStore(libraryDirectory: LibraryDirectory) : AssetFiles {
  private val root = libraryDirectory.path().resolve(ASSETS_DIRECTORY)
  private val staging = root.resolve(STAGING_DIRECTORY)

  override fun pathOf(hash: AssetHash): String = fileOf(hash).toString()

  fun exists(hash: AssetHash): Boolean = Files.isRegularFile(fileOf(hash))

  fun open(hash: AssetHash): InputStream = StoreInputStream(storeIo { Files.newInputStream(fileOf(hash)) })

  fun size(hash: AssetHash): Long = storeIo { Files.size(fileOf(hash)) }

  fun stage(): AssetStaging = storeIo {
    Files.createDirectories(staging)
    AssetStaging(Files.createTempFile(staging, "asset", ".tmp"))
  }

  fun <T> withTemporaryFile(use: (Path) -> T): T {
    val file = storeIo {
      Files.createDirectories(staging)
      Files.createTempFile(staging, "import", ".tmp")
    }
    try {
      return use(file)
    } finally {
      deleteLeavingItToThePurge(file)
    }
  }

  fun commit(staged: StagedAsset) = storeIo {
    val target = fileOf(staged.hash)
    Files.createDirectories(target.parent)
    try {
      Files.move(staged.file, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: FileAlreadyExistsException) {
      Files.deleteIfExists(staged.file)
    }
  }

  fun storedFiles(): List<StoredAssetFile> = storeIo {
    if (!Files.isDirectory(root)) return@storeIo emptyList()
    root.listDirectoryEntries().filter { it.name != STAGING_DIRECTORY && Files.isDirectory(it) }
      .flatMap { it.listDirectoryEntries() }
      .filter { it.isRegularFile() && HASH_NAME.matches(it.name) }
      .map { StoredAssetFile(AssetHash(it.name), it.getLastModifiedTime().toMillis()) }
  }

  fun stagingFiles(): List<StoredStagingFile> = storeIo {
    if (!Files.isDirectory(staging)) return@storeIo emptyList()
    staging.listDirectoryEntries().map { StoredStagingFile(it, it.getLastModifiedTime().toMillis()) }
  }

  fun delete(hash: AssetHash) = storeIo { Files.deleteIfExists(fileOf(hash)) }

  private fun fileOf(hash: AssetHash): Path = root.resolve(hash.hex.take(FAN_OUT_LENGTH)).resolve(hash.hex)
}

internal class StoredAssetFile(val hash: AssetHash, val modifiedAt: Long)

internal class StoredStagingFile(val file: Path, val modifiedAt: Long)

internal class StagedAsset(val hash: AssetHash, val file: Path) {
  fun discard() = deleteLeavingItToThePurge(file)
}

internal fun deleteLeavingItToThePurge(file: Path) = leavingFailuresToThePurge { Files.deleteIfExists(file) }

private fun leavingFailuresToThePurge(work: () -> Unit) {
  try {
    storeIo(work)
  } catch (_: StoreIoException) {
    return
  } catch (_: StorageFullException) {
    return
  }
}

internal class AssetStaging(private val file: Path) : OutputStream() {
  private val digest = MessageDigest.getInstance("SHA-256")
  private val channel = FileChannel.open(file, StandardOpenOption.WRITE)
  private val target = DigestOutputStream(Channels.newOutputStream(channel).buffered(), digest)
  private var closed = false

  override fun write(b: Int) = storeIo { target.write(b) }

  override fun write(b: ByteArray, off: Int, len: Int) = storeIo { target.write(b, off, len) }

  override fun close() {
    if (closed) return
    closed = true
    storeIo {
      target.flush()
      channel.force(true)
      target.close()
    }
  }

  fun finish(): StagedAsset {
    close()
    return StagedAsset(AssetHash(digest.digest().toHexString()), file)
  }

  fun abandon() {
    leavingFailuresToThePurge { close() }
    deleteLeavingItToThePurge(file)
  }
}

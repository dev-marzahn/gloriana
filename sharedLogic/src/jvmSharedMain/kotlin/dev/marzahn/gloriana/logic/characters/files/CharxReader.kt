package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.StagedAsset
import dev.marzahn.gloriana.logic.characters.CardFileRead
import dev.marzahn.gloriana.logic.characters.DamagedAs
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.ImportedCard
import dev.marzahn.gloriana.logic.characters.SizeLimit
import dev.marzahn.gloriana.logic.characters.card.CardAssetEntry
import dev.marzahn.gloriana.logic.characters.card.CardJsonRead
import dev.marzahn.gloriana.logic.characters.card.ParsedCard
import dev.marzahn.gloriana.logic.files.JSON_FILE_LIMIT_BYTES
import java.io.InputStream
import java.nio.file.Path

private const val CARD_ENTRY = "card.json"
private const val CHARX_EXTENSION = ".charx"
private const val EMBEDDED_URI_PREFIX = "embeded://"
private const val MAIN_ICON_NAME = "main"

internal class EntryLargerThanDeclared : RuntimeException("a zip entry unpacks past its declared size")

internal class CharxReader(private val staging: Staging) {
  fun read(fileName: String, zipFile: Path): CardFileRead = ZipArchive.open(zipFile).use { zip ->
    when (val checked = ZipCheck.check(zip.entries)) {
      is ZipCheck.Refused -> CardFileRead.Failed(checked.reason)
      is ZipCheck.Safe -> readCard(fileName, zip, checked)
    }
  }

  private fun readCard(fileName: String, zip: ZipArchive, entries: ZipCheck.Safe): CardFileRead {
    val cardEntry = entries.byPath[CARD_ENTRY] ?: return CardFileRead.Failed(missingCard(fileName))
    val json = zip.declaredContent(cardEntry).use { it.readBytes() }
    return when (val card = CardJson.read(json)) {
      is CardJsonRead.Card -> CardFileRead.Card(imported(card.card, zip, entries))
      CardJsonRead.Damaged -> CardFileRead.Failed(ImportFailure.Damaged(DamagedAs.Zip))
      CardJsonRead.NotACard -> CardFileRead.Failed(ImportFailure.NotACharacterCard)
    }
  }

  private fun missingCard(fileName: String) = when {
    fileName.endsWith(CHARX_EXTENSION, ignoreCase = true) -> ImportFailure.Damaged(DamagedAs.CharxWithoutCard)
    else -> ImportFailure.NotACharacterCard
  }

  private fun imported(card: ParsedCard, zip: ZipArchive, entries: ZipCheck.Safe): ImportedCard {
    val staged = mutableMapOf<String, StagedAsset>()
    val resolved = card.assets.map { asset ->
      val file = asset.embeddedPath()?.let { path ->
        entries.byPath[path]?.takeUnless(ZipEntryInfo::isDirectory)?.let { entry ->
          staged.getOrPut(path) { staging.file { out -> zip.declaredContent(entry).use { it.copyTo(out) } } }
        }
      }
      ResolvedAsset(asset.toAsset(file), file)
    }
    val avatarIndex = avatarIndex(card.assets, resolved)
    val avatar = avatarIndex?.let { resolved[it].file }
    val others = resolved.filterIndexed { index, _ -> index != avatarIndex }
    return ImportedCard.WithFiles(
      parsed = card,
      avatar = avatar?.hash,
      assets = others.map { it.asset },
      staged = staging.keep(listOfNotNull(avatar) + others.mapNotNull { it.file }),
    )
  }

  private fun avatarIndex(entries: List<CardAssetEntry>, resolved: List<ResolvedAsset>): Int? {
    val storedIcons = entries.indices.filter { entries[it].type == ICON_ASSET_TYPE && resolved[it].file != null }
    return storedIcons.firstOrNull { entries[it].name == MAIN_ICON_NAME } ?: storedIcons.firstOrNull()
  }

  private fun CardAssetEntry.embeddedPath(): String? =
    uri?.takeIf { it.startsWith(EMBEDDED_URI_PREFIX) }?.removePrefix(EMBEDDED_URI_PREFIX)?.let(ZipPath::of)
}

internal object ZipCheck {
  sealed interface Result

  class Refused(val reason: ImportFailure) : Result

  class Safe(val byPath: Map<String, ZipEntryInfo>) : Result

  fun check(all: List<ZipEntryInfo>): Result {
    val paths = all.map { it to ZipPath.of(it.name) }
    val cardEntries = paths.filter { (_, path) -> path == CARD_ENTRY }.map { it.first }
    val tooLarge = listOfNotNull(
      SizeLimit.CardJson.takeIf { cardEntries.any { it.size > JSON_FILE_LIMIT_BYTES } },
      SizeLimit.ZipEntryCount.takeIf { all.size > CardLimits.ZIP_ENTRIES },
      SizeLimit.ZipEntrySize.takeIf { all.any { it.size > CardLimits.ZIP_ENTRY_BYTES } },
    )
    return when {
      paths.any { (entry, path) -> path == null || expandsTooMuch(entry) } -> Refused(ImportFailure.Unsafe)
      tooLarge.isNotEmpty() -> Refused(ImportFailure.TooLarge(tooLarge.first()))
      else -> Safe(paths.mapNotNull { (entry, path) -> path?.let { it to entry } }.toMap())
    }
  }

  private fun expandsTooMuch(entry: ZipEntryInfo) = entry.size > entry.compressedSize * CardLimits.ZIP_EXPANSION_RATIO
}

internal object ZipPath {
  private val DRIVE = Regex("^[A-Za-z]:")

  fun of(name: String): String? {
    val slashed = name.replace('\\', '/')
    val absolute = slashed.startsWith("/") || DRIVE.containsMatchIn(slashed)
    return if (absolute) null else withinRoot(slashed.split('/'))
  }

  private fun withinRoot(segments: List<String>): String? {
    val kept = ArrayDeque<String>()
    var escapes = false
    for (segment in segments) {
      when (segment) {
        "", "." -> Unit
        ".." -> escapes = escapes || kept.removeLastOrNull() == null
        else -> kept.addLast(segment)
      }
    }
    return if (escapes) null else kept.joinToString("/")
  }
}

private fun ZipArchive.declaredContent(entry: ZipEntryInfo): InputStream {
  val content = open(entry)
  return object : InputStream() {
    private val limited = LimitedInputStream(content, entry.size)

    override fun read(): Int = limited.read().also { if (it == -1) checkNothingLeft() }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
      limited.read(b, off, len).also { if (it == -1) checkNothingLeft() }

    override fun close() = content.close()

    private fun checkNothingLeft() {
      if (limited.reachedLimit && content.read() != -1) throw EntryLargerThanDeclared()
    }
  }
}

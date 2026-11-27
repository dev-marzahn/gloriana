package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.characters.card.ParsedCard
import dev.marzahn.gloriana.logic.characters.storage.CharacterContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal interface CardReader {
  fun read(file: PickedFile): CardFileRead
}

internal interface CardWriter {
  fun write(card: CardForExport, format: CardFormat, destination: ExportDestination): ExportResult
}

internal sealed interface CardFileRead {
  class Card(val card: ImportedCard) : CardFileRead

  data class Failed(val reason: ImportFailure) : CardFileRead
}

internal data class CardForExport(val character: Character, val verbatim: JsonObject)

internal interface StagedFiles {
  fun commit()

  fun discard()
}

internal sealed class ImportedCard : AutoCloseable {
  abstract val parsed: ParsedCard

  abstract fun commit()

  abstract fun newCharacter(): CharacterContent

  abstract fun replacing(previous: Character): CharacterContent

  protected fun content(avatar: AssetHash?, assets: List<CharacterAsset>, pronouns: Pronouns) = CharacterContent(
    fields = parsed.fields,
    avatar = avatar,
    assets = assets,
    pronouns = pronouns,
    kept = parsed.kept,
    cardJson = Json.encodeToString(parsed.verbatim),
  )

  class WithFiles(
    override val parsed: ParsedCard,
    private val avatar: AssetHash?,
    private val assets: List<CharacterAsset>,
    private val staged: StagedFiles,
  ) : ImportedCard() {
    private var committed = false

    override fun commit() {
      staged.commit()
      committed = true
    }

    override fun newCharacter() = content(avatar, assets, parsed.pronouns)

    override fun replacing(previous: Character) = content(avatar, assets, previous.pronouns)

    override fun close() {
      if (!committed) staged.discard()
    }
  }

  class JsonOnly(override val parsed: ParsedCard, private val assets: List<CharacterAsset>) : ImportedCard() {
    override fun commit() = Unit

    override fun newCharacter() = content(avatar = null, assets, parsed.pronouns)

    override fun replacing(previous: Character): CharacterContent {
      val storedFiles = previous.assets.filter { it.source is AssetSource.Stored }
      val storedNames = storedFiles.map { it.name to it.extension }.toSet()
      val fromJson = assets.filterNot { it.name to it.extension in storedNames }
      return content(previous.avatar, fromJson + storedFiles, previous.pronouns)
    }

    override fun close() = Unit
  }
}

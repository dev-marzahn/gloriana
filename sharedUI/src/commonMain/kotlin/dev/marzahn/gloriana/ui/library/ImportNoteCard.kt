package dev.marzahn.gloriana.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import dev.marzahn.gloriana.logic.characters.DamagedAs
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.SizeLimit
import dev.marzahn.gloriana.ui.characters.FailedFile
import dev.marzahn.gloriana.ui.characters.ImportNote
import dev.marzahn.gloriana.ui.characters.NoteCard
import dev.marzahn.gloriana.ui.characters.NoteLine
import dev.marzahn.gloriana.ui.characters.boldThenPlain
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.import_choose_again
import dev.marzahn.gloriana.ui.resources.import_choose_another
import dev.marzahn.gloriana.ui.resources.import_choose_others
import dev.marzahn.gloriana.ui.resources.import_failed_all
import dev.marzahn.gloriana.ui.resources.import_failed_some
import dev.marzahn.gloriana.ui.resources.import_timed_out
import dev.marzahn.gloriana.ui.resources.import_timed_out_text
import dev.marzahn.gloriana.ui.resources.reason_cant_open
import dev.marzahn.gloriana.ui.resources.reason_cant_open_detail
import dev.marzahn.gloriana.ui.resources.reason_damaged_json
import dev.marzahn.gloriana.ui.resources.reason_damaged_no_card
import dev.marzahn.gloriana.ui.resources.reason_damaged_png
import dev.marzahn.gloriana.ui.resources.reason_damaged_zip
import dev.marzahn.gloriana.ui.resources.reason_not_a_card
import dev.marzahn.gloriana.ui.resources.reason_not_a_card_detail
import dev.marzahn.gloriana.ui.resources.reason_not_saved
import dev.marzahn.gloriana.ui.resources.reason_not_saved_detail
import dev.marzahn.gloriana.ui.resources.reason_storage_full
import dev.marzahn.gloriana.ui.resources.reason_storage_full_detail
import dev.marzahn.gloriana.ui.resources.reason_too_large_card
import dev.marzahn.gloriana.ui.resources.reason_too_large_items
import dev.marzahn.gloriana.ui.resources.reason_too_large_unpacked
import dev.marzahn.gloriana.ui.resources.reason_unsafe
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private class FailureWords(val headline: StringResource, val detail: StringResource? = null)

private val DamagedWords = mapOf(
  DamagedAs.Png to Res.string.reason_damaged_png,
  DamagedAs.Json to Res.string.reason_damaged_json,
  DamagedAs.Zip to Res.string.reason_damaged_zip,
  DamagedAs.CharxWithoutCard to Res.string.reason_damaged_no_card,
)

private val TooLargeWords = mapOf(
  SizeLimit.CardJson to Res.string.reason_too_large_card,
  SizeLimit.ZipEntryCount to Res.string.reason_too_large_items,
  SizeLimit.ZipEntrySize to Res.string.reason_too_large_unpacked,
)

private fun ImportFailure.words(): FailureWords = when (this) {
  ImportFailure.CantBeOpened -> FailureWords(Res.string.reason_cant_open, Res.string.reason_cant_open_detail)
  is ImportFailure.Damaged -> FailureWords(DamagedWords.getValue(readAs))
  ImportFailure.NotACharacterCard -> FailureWords(Res.string.reason_not_a_card, Res.string.reason_not_a_card_detail)
  is ImportFailure.TooLarge -> FailureWords(TooLargeWords.getValue(limit))
  ImportFailure.Unsafe -> FailureWords(Res.string.reason_unsafe)
  ImportFailure.StorageFull -> FailureWords(Res.string.reason_storage_full, Res.string.reason_storage_full_detail)
  ImportFailure.CouldNotSave -> FailureWords(Res.string.reason_not_saved, Res.string.reason_not_saved_detail)
}

@Composable
fun failureMessage(failed: FailedFile, headlineBold: Boolean): AnnotatedString {
  val words = failed.reason.words()
  val headline = stringResource(words.headline, failed.name)
  val detail = words.detail?.let { stringResource(it) }
  val plain = listOfNotNull(headline, detail).joinToString(" ")
  return if (headlineBold) boldThenPlain(headline, detail) else AnnotatedString(plain)
}

@Composable
fun ImportNoteCard(note: ImportNote, onChoose: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  when (note) {
    is ImportNote.Failed -> {
      val single = note.picked == 1
      val failed = note.failures.size
      val headline = when {
        single -> null
        failed == note.picked -> stringResource(Res.string.import_failed_all, note.picked)
        else -> pluralStringResource(Res.plurals.import_failed_some, failed, failed, note.picked)
      }
      val button = if (single) Res.string.import_choose_another else Res.string.import_choose_others
      NoteCard(modifier, onDismiss) {
        headline?.let { NoteLine(boldThenPlain(it, null)) }
        note.failures.forEach { NoteLine(failureMessage(it, headlineBold = single)) }
        GlorianaButton(stringResource(button), onChoose)
      }
    }
    is ImportNote.TimedOut -> NoteCard(modifier, onDismiss) {
      val headline = pluralStringResource(Res.plurals.import_timed_out, note.notImported, note.notImported, note.picked)
      NoteLine(boldThenPlain(headline, stringResource(Res.string.import_timed_out_text)))
      note.cutOff.forEach { NoteLine(AnnotatedString(it)) }
      note.failures.forEach { NoteLine(failureMessage(it, headlineBold = false)) }
      GlorianaButton(stringResource(Res.string.import_choose_again), onChoose)
    }
  }
}

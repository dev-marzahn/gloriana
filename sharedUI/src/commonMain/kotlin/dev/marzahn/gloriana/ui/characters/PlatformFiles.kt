package dev.marzahn.gloriana.ui.characters

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.PickedFile

fun interface CardFilePicker {
  fun pick()
}

fun interface CardSaver {
  fun choose(proposedName: String, format: CardFormat)
}

@Composable
expect fun rememberCardFilePicker(onPick: (List<PickedFile>) -> Unit): CardFilePicker

@Composable
expect fun rememberCardSaver(onChoose: (SaveTarget) -> Unit): CardSaver

expect fun decodeImageFile(path: String, maxSidePx: Int): ImageBitmap?

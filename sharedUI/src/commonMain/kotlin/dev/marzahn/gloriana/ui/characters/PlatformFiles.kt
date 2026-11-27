package dev.marzahn.gloriana.ui.characters

import androidx.compose.ui.graphics.ImageBitmap
import dev.marzahn.gloriana.ui.files.FileChoice

val CardFiles = FileChoice(listOf("png", "json", "charx"), several = true)

expect fun decodeImageFile(path: String, maxSidePx: Int): ImageBitmap?

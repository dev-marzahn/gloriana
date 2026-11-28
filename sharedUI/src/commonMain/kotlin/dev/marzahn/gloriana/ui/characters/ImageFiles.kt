package dev.marzahn.gloriana.ui.characters

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageFile(path: String, maxSidePx: Int): ImageBitmap?

package dev.marzahn.gloriana.logic.assets

fun interface AssetFiles {
  fun pathOf(hash: AssetHash): String
}

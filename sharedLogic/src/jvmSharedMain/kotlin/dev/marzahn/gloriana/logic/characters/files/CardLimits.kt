package dev.marzahn.gloriana.logic.characters.files

internal object CardLimits {
  const val CARD_JSON_BYTES = 16L * 1024 * 1024
  const val CARD_JSON_BASE64_CHARACTERS = (CARD_JSON_BYTES + 2) / 3 * 4
  const val ZIP_ENTRIES = 10_000
  const val ZIP_ENTRY_BYTES = 512L * 1024 * 1024
  const val ZIP_EXPANSION_RATIO = 100
  const val AVATAR_PIXELS = 16L * 1024 * 1024
}

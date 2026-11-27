package dev.marzahn.gloriana.logic.assets

import kotlin.jvm.JvmInline

@JvmInline
value class AssetHash(val hex: String) {
  init {
    require(SHA_256_HEX.matches(hex)) { "An asset hash is 64 lowercase hex digits" }
  }

  private companion object {
    val SHA_256_HEX = Regex("[0-9a-f]{64}")
  }
}

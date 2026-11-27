package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetStaging
import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.assets.StagedAsset
import dev.marzahn.gloriana.logic.characters.StagedFiles

internal class Staging(private val store: AssetStore) {
  private val writing = mutableListOf<AssetStaging>()
  private val finished = mutableListOf<StagedAsset>()

  fun file(write: (AssetStaging) -> Unit): StagedAsset {
    val staging = store.stage()
    writing += staging
    staging.use(write)
    val staged = staging.finish()
    writing -= staging
    finished += staged
    return staged
  }

  fun keep(kept: Collection<StagedAsset>): StagedFiles {
    val keptFiles = kept.toSet()
    finished.filterNot { it in keptFiles }.forEach(StagedAsset::discard)
    finished.clear()
    return object : StagedFiles {
      override fun commit() = keptFiles.forEach(store::commit)

      override fun discard() = keptFiles.forEach(StagedAsset::discard)
    }
  }

  fun discardAll() {
    writing.forEach(AssetStaging::abandon)
    finished.forEach(StagedAsset::discard)
    writing.clear()
    finished.clear()
  }
}

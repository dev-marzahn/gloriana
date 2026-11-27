package dev.marzahn.gloriana.ui.characters

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.meta_creator
import dev.marzahn.gloriana.ui.resources.meta_creator_version
import dev.marzahn.gloriana.ui.resources.meta_version
import org.jetbrains.compose.resources.stringResource

@Composable
fun metaLine(creator: String, version: String): String? = when {
  creator.isNotBlank() && version.isNotBlank() -> stringResource(Res.string.meta_creator_version, creator, version)
  creator.isNotBlank() -> stringResource(Res.string.meta_creator, creator)
  version.isNotBlank() -> stringResource(Res.string.meta_version, version)
  else -> null
}

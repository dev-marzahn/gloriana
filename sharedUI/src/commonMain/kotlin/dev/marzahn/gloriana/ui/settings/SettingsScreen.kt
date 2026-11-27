package dev.marzahn.gloriana.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_tune
import dev.marzahn.gloriana.ui.resources.settings_empty_heading
import dev.marzahn.gloriana.ui.resources.settings_empty_text
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
  val empty = EmptyBlock(
    icon = Res.drawable.ic_tune,
    heading = stringResource(Res.string.settings_empty_heading),
    text = stringResource(Res.string.settings_empty_text),
  )
  TopLevelScreen(Destination.Settings, TopLevelContent.Empty(empty), modifier)
}

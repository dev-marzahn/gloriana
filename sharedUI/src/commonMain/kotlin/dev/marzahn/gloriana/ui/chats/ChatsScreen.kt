package dev.marzahn.gloriana.ui.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.chats_empty_heading
import dev.marzahn.gloriana.ui.resources.chats_empty_text
import dev.marzahn.gloriana.ui.resources.ic_forum
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatsScreen(modifier: Modifier = Modifier) {
  val empty = EmptyBlock(
    icon = Res.drawable.ic_forum,
    heading = stringResource(Res.string.chats_empty_heading),
    text = stringResource(Res.string.chats_empty_text),
  )
  TopLevelScreen(Destination.Chats, TopLevelContent.Empty(empty), modifier)
}

package dev.marzahn.gloriana.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AvatarShape { Circle, Tile }

enum class AvatarSize(val side: Dp, val shape: AvatarShape) {
  List(48.dp, AvatarShape.Circle),
  ListPane(36.dp, AvatarShape.Circle),
  Header(56.dp, AvatarShape.Tile),
}

package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.theme.GlorianaTheme

@Composable
fun LazyItemScope.listMotion(): Modifier {
  val motion = GlorianaTheme.motion
  return Modifier.animateItem(
    fadeInSpec = motion.enter.tween(),
    placementSpec = motion.emphasized.tween(),
    fadeOutSpec = motion.exit.tween(),
  )
}

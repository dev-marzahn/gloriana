package dev.marzahn.gloriana.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.default_mark
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun DefaultMark(isDefault: Boolean, modifier: Modifier = Modifier) {
  val standard = GlorianaTheme.motion.standard
  AnimatedVisibility(
    isDefault,
    modifier,
    enter = fadeIn(standard.tween()) + expandHorizontally(standard.tween()),
    exit = fadeOut(standard.tween()) + shrinkHorizontally(standard.tween()),
  ) {
    Mark(stringResource(Res.string.default_mark))
  }
}

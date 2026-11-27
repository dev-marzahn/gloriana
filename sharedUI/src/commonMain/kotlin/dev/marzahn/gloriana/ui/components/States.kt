package dev.marzahn.gloriana.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_error
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val EmptyIconSize = 40.dp
private val EmptyBlockWidth = 320.dp
private const val SKELETON_WAIT_MILLIS = 300L

data class EmptyAction(val label: String, val icon: DrawableResource?, val onClick: () -> Unit)

@Immutable
data class EmptyBlock(
  val icon: DrawableResource,
  val heading: String,
  val text: String,
  val action: EmptyAction? = null,
)

@Composable
fun EmptyState(block: EmptyBlock, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  val action = block.action
  Box(modifier, contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = EmptyBlockWidth), horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(painterResource(block.icon), null, Modifier.size(EmptyIconSize), tint = colors.textSubtle)
      Spacer(Modifier.height(spacing.s16))
      Text(block.heading, style = type.titleLarge, color = colors.text, textAlign = TextAlign.Center)
      Spacer(Modifier.height(spacing.s8))
      Text(block.text, style = type.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
      if (action != null) {
        Spacer(Modifier.height(spacing.s24))
        GlorianaButton(action.label, action.onClick, kind = ButtonKind.Primary, icon = action.icon)
      }
    }
  }
}

@Composable
fun Skeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(SKELETON_WAIT_MILLIS)
    shown = true
  }
  AnimatedVisibility(shown, modifier, enter = fadeIn(GlorianaTheme.motion.standard.tween())) { content() }
}

enum class SkeletonSurface { Canvas, Raised }

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, on: SkeletonSurface = SkeletonSurface.Canvas) {
  val colors = GlorianaTheme.colors
  val color = if (on == SkeletonSurface.Raised) colors.surface3 else colors.surface2
  Box(modifier.background(color, GlorianaTheme.shapes.control))
}

@Composable
fun ErrorNote(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.control
  Row(
    modifier
      .fillMaxWidth()
      .background(colors.dangerSoft, shape)
      .border(1.dp, colors.dangerBorder, shape)
      .padding(spacing.s12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    Icon(painterResource(Res.drawable.ic_error), null, tint = colors.dangerText)
    Text(text, Modifier.weight(1f), style = GlorianaTheme.type.material.bodyMedium, color = colors.dangerText)
    if (onRetry != null) GlorianaButton(stringResource(Res.string.retry), onRetry)
  }
}

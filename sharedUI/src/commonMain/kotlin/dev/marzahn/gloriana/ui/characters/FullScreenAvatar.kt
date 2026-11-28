package dev.marzahn.gloriana.ui.characters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.navigation.ShellInsets
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.close
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2f
private const val SWIPE_TO_CLOSE_PX = 120f
private val CloseSize = 48.dp

@Composable
fun FullScreenAvatar(name: String, avatarPath: String, onClose: () -> Unit) {
  Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
    HideSystemBars()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val motion = GlorianaTheme.motion
    AnimatedVisibility(shown, enter = fadeIn(motion.standard.tween()), exit = fadeOut(motion.standard.tween())) {
      Viewer(name, avatarPath, onClose)
    }
  }
}

@Composable
private fun Viewer(name: String, avatarPath: String, onClose: () -> Unit) {
  val colors = GlorianaTheme.colors
  var zoom by remember { mutableFloatStateOf(1f) }
  var pan by remember { mutableStateOf(Offset.Zero) }
  var dragged by remember { mutableFloatStateOf(0f) }
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(colors.pictureShade)
      .onKeyEvent { event -> (event.key == Key.Escape).also { if (it) onClose() } },
  ) {
    val maxSidePx = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() * 2 }
    val image by rememberImageFile(avatarPath, maxSidePx)
    image?.let { bitmap ->
      Image(
        bitmap,
        null,
        Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            detectTransformGestures { _, panChange, zoomChange, _ ->
              zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
              pan = if (zoom > 1f) pan + panChange else Offset.Zero
            }
          }
          .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
              zoom = if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM
              pan = Offset.Zero
            })
          }
          .pointerInput(zoom) {
            if (zoom == 1f) {
              detectVerticalDragGestures(
                onDragEnd = { if (dragged > SWIPE_TO_CLOSE_PX) onClose() else dragged = 0f },
                onVerticalDrag = { _, amount -> dragged = (dragged + amount).coerceAtLeast(0f) },
              )
            }
          }
          .graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            translationX = pan.x
            translationY = pan.y + dragged
          },
        contentScale = ContentScale.Fit,
      )
    }
    TopBar(name, onClose, Modifier.align(Alignment.TopStart))
  }
}

@Composable
private fun TopBar(name: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
  val onImage = GlorianaTheme.colors.onAvatar
  Row(
    modifier
      .fillMaxWidth()
      .windowInsetsPadding(ShellInsets.safeDrawingWithoutKeyboard)
      .padding(GlorianaTheme.spacing.s8),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val shape = GlorianaTheme.shapes.control
    Box(Modifier.size(CloseSize).tappable(shape, onClick = onClose), contentAlignment = Alignment.Center) {
      Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.close), tint = onImage)
    }
    Text(name, style = GlorianaTheme.type.material.titleMedium, color = onImage, maxLines = 1)
  }
}

@Composable
internal expect fun HideSystemBars()

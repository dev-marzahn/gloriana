package dev.marzahn.gloriana.ui.characters

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.marzahn.gloriana.ui.components.avatarGradient
import dev.marzahn.gloriana.ui.components.avatarInitial
import dev.marzahn.gloriana.ui.components.brushFromTopStart
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_person
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.uuid.Uuid

private const val LETTER_SHARE = 0.4f
private const val FACES_ALIGNMENT = -1f / 3f
private val TopThird = BiasAlignment(horizontalBias = 0f, verticalBias = FACES_ALIGNMENT)

@Composable
fun rememberImageFile(path: String?, maxSidePx: Int): State<ImageBitmap?> =
  produceState<ImageBitmap?>(null, path, maxSidePx) {
    value = path?.let { withContext(Dispatchers.IO) { decodeImageFile(it, maxSidePx) } }
  }

@Composable
fun CharacterPicture(
  id: Uuid,
  name: String,
  avatarPath: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
) {
  BoxWithConstraints(modifier) {
    val maxSidePx = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }
    val image by rememberImageFile(avatarPath, maxSidePx)
    val loaded = image
    if (loaded == null) {
      LetterPicture(id, name, Modifier.fillMaxSize())
    } else {
      Image(loaded, null, Modifier.fillMaxSize(), alignment = TopThird, contentScale = contentScale)
    }
  }
}

@Composable
fun LetterPicture(id: Uuid, name: String, modifier: Modifier = Modifier) {
  val brush = avatarGradient(id).brushFromTopStart(LocalLayoutDirection.current)
  BoxWithConstraints(modifier.background(brush)) {
    val side = maxWidth * LETTER_SHARE
    val initial = avatarInitial(name, Locale.current)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (initial == null) {
        Icon(painterResource(Res.drawable.ic_person), null, tint = GlorianaTheme.colors.onAvatar)
      } else {
        val letterSize = side.value.sp
        val style = TextStyle(fontFamily = GlorianaTheme.fonts.display, fontSize = letterSize, lineHeight = letterSize)
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
          Text(initial, style = style, color = GlorianaTheme.colors.onAvatar)
        }
      }
    }
  }
}


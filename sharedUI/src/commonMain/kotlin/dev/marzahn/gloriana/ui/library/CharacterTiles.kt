package dev.marzahn.gloriana.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.characters.CharacterPicture
import dev.marzahn.gloriana.ui.characters.RunningFile
import dev.marzahn.gloriana.ui.characters.RunningStatus
import dev.marzahn.gloriana.ui.components.ButtonSize
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.RunningLine
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.ic_add
import dev.marzahn.gloriana.ui.resources.ic_draft
import dev.marzahn.gloriana.ui.resources.import_announce
import dev.marzahn.gloriana.ui.resources.import_choose
import dev.marzahn.gloriana.ui.resources.import_importing
import dev.marzahn.gloriana.ui.resources.import_needs_answer
import dev.marzahn.gloriana.ui.resources.import_waiting
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.lookShadow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TILE_RATIO = 3f / 4f
private const val FADE_AT_TEXT = 0.55f
private const val FADE_AT_BOTTOM = 0.72f
private const val DASH_ON = 6f
private const val SHORT_CAPTION = 0.6f
private const val DASH_OFF = 4f
private val DraftIconSize = 32.dp
private val SelectedRing = 2.dp
private val SelectedInnerLine = 1.dp

class TileMetrics(val textInset: Dp, val fadeAbove: Dp, val inPane: Boolean)

val PhoneTile = TileMetrics(textInset = 8.dp, fadeAbove = 24.dp, inPane = false)
val PaneTile = TileMetrics(textInset = 6.dp, fadeAbove = 16.dp, inPane = true)

@Composable
private fun TileFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  val shape = GlorianaTheme.shapes.control
  Box(
    modifier
      .aspectRatio(TILE_RATIO)
      .lookShadow(GlorianaTheme.shadows.soft, shape)
      .clip(shape)
      .border(1.dp, GlorianaTheme.colors.border, shape),
    content = content,
  )
}

@Composable
fun CharacterTileView(
  tile: CharacterTile,
  selected: Boolean,
  metrics: TileMetrics,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = GlorianaTheme.shapes.control
  val colors = GlorianaTheme.colors
  TileFrame(
    modifier
      .semantics { this.selected = selected }
      .tappable(shape, onClick = onClick),
  ) {
    CharacterPicture(tile.id, tile.name, tile.avatarPath, Modifier.fillMaxSize())
    TileName(tile.name, metrics, Modifier.align(Alignment.BottomStart))
    if (selected) {
      Box(Modifier.fillMaxSize().border(SelectedRing, colors.accent, shape))
      Box(Modifier.fillMaxSize().padding(SelectedRing).border(SelectedInnerLine, colors.raised, shape))
    }
  }
}

@Composable
private fun TileName(name: String, metrics: TileMetrics, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val fadeAbovePx = with(LocalDensity.current) { metrics.fadeAbove.toPx() }
  val style = if (metrics.inPane) GlorianaTheme.type.material.labelMedium else GlorianaTheme.type.material.titleSmall
  Box(
    modifier
      .fillMaxWidth()
      .drawBehind {
        val textStart = (fadeAbovePx / size.height).coerceIn(0f, 1f)
        drawRect(
          Brush.verticalGradient(
            0f to colors.pictureShade.copy(alpha = 0f),
            textStart to colors.pictureShade.copy(alpha = FADE_AT_TEXT),
            1f to colors.pictureShade.copy(alpha = FADE_AT_BOTTOM),
          ),
        )
      }
      .padding(start = metrics.textInset, end = metrics.textInset, bottom = metrics.textInset, top = metrics.fadeAbove),
  ) {
    Text(name, style = style, color = colors.onAvatar, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
fun RunningTileView(file: RunningFile, metrics: TileMetrics, onChoose: () -> Unit, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val announcement = stringResource(Res.string.import_announce, file.name, file.position, file.of)
  val live = if (file.status == RunningStatus.Importing) {
    Modifier.semantics {
      liveRegion = LiveRegionMode.Polite
      contentDescription = announcement
    }
  } else {
    Modifier
  }
  TileFrame(modifier.background(colors.surface2).then(live)) {
    if (file.status != RunningStatus.NeedsAnswer) {
      Icon(
        painterResource(Res.drawable.ic_draft),
        null,
        Modifier.align(Alignment.TopCenter).padding(top = DraftIconSize).size(DraftIconSize),
        tint = colors.textSubtle,
      )
    }
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(metrics.textInset)) {
      Text(
        file.name,
        style = GlorianaTheme.type.material.titleSmall,
        color = colors.text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(GlorianaTheme.spacing.s6 / 2))
      RunningLine(file.status, onChoose)
    }
  }
}

@Composable
private fun RunningLine(status: RunningStatus, onChoose: () -> Unit) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  when (status) {
    RunningStatus.Importing -> RunningLine(stringResource(Res.string.import_importing))
    RunningStatus.Waiting ->
      Text(stringResource(Res.string.import_waiting), style = type.bodySmall, color = colors.textSubtle)
    RunningStatus.NeedsAnswer -> Column {
      Text(stringResource(Res.string.import_needs_answer), style = type.bodySmall, color = colors.accentText)
      GlorianaButton(stringResource(Res.string.import_choose), onChoose, size = ButtonSize.Small)
    }
  }
}

@Composable
fun CreateTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.control
  val radius = GlorianaTheme.shapes.controlRadius
  Box(
    modifier
      .aspectRatio(TILE_RATIO)
      .clip(shape)
      .drawBehind {
        drawRoundRect(
          colors.borderStrong,
          cornerRadius = CornerRadius(radius.toPx()),
          style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))),
        )
      }
      .tappable(shape, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(painterResource(Res.drawable.ic_add), null, tint = colors.textMuted)
      Text(stringResource(Res.string.create), style = GlorianaTheme.type.material.labelLarge, color = colors.textMuted)
    }
  }
}

@Composable
fun SkeletonTile(modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Box(modifier.aspectRatio(TILE_RATIO).background(colors.surface2, GlorianaTheme.shapes.control)) {
    val bar = Modifier.height(spacing.s10)
    val barShape = GlorianaTheme.shapes.tag
    Column(
      Modifier.align(Alignment.BottomStart).padding(spacing.s8),
      verticalArrangement = Arrangement.spacedBy(spacing.s6),
    ) {
      Box(bar.fillMaxWidth().background(colors.surface3, barShape))
      Box(bar.fillMaxWidth(SHORT_CAPTION).background(colors.surface3, barShape))
    }
  }
}

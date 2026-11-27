package dev.marzahn.gloriana.ui.characters.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.details_summary
import dev.marzahn.gloriana.ui.resources.ic_chevron_right
import dev.marzahn.gloriana.ui.resources.ic_expand_more
import dev.marzahn.gloriana.ui.resources.ic_image
import dev.marzahn.gloriana.ui.resources.image_not_loaded
import dev.marzahn.gloriana.ui.resources.image_not_loaded_alt
import dev.marzahn.gloriana.ui.characters.tappable
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val LAST_SMALL_HEADING = 3
private const val CODE_BLOCK = 0.9f
private const val DASH_ON = 6f
private const val DASH_OFF = 4f
private val QuoteBar = 2.dp
private val QuoteInset = 12.dp

@Composable
fun FieldContent(blocks: List<FieldBlock>, panel: Color, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    blocks.forEach { FieldBlockView(it, panel) }
  }
}

@Composable
private fun FieldBlockView(block: FieldBlock, panel: Color) {
  val reading = GlorianaTheme.type.reading
  val colors = GlorianaTheme.colors
  when (block) {
    is FieldBlock.Paragraph -> FieldText(block.inlines, reading, panel)
    is FieldBlock.Heading -> FieldText(block.inlines, headingStyle(block.level), panel)
    is FieldBlock.ItemList -> ItemListView(block, panel)
    is FieldBlock.Quote -> {
      val bar = Modifier.drawBehind { drawRect(colors.borderStrong, size = size.copy(width = QuoteBar.toPx())) }
      FieldContent(block.blocks, panel, Modifier.fillMaxWidth().then(bar).padding(start = QuoteInset))
    }
    is FieldBlock.Preformatted -> Preformatted(block.text)
    FieldBlock.Rule -> HorizontalDivider(thickness = 1.dp, color = colors.border)
    is FieldBlock.Details -> DetailsView(block, panel)
    is FieldBlock.ImageNotLoaded -> ImagePlaceholder(block.alt)
    is FieldBlock.AsWritten -> Text(block.text, style = reading, color = colors.textMuted)
  }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
  val material = GlorianaTheme.type.material
  val display = GlorianaTheme.fonts.display
  return if (level <= LAST_SMALL_HEADING) material.titleLarge else material.titleMedium.copy(fontFamily = display)
}

@Composable
private fun ItemListView(list: FieldBlock.ItemList, panel: Color) {
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
    list.items.forEachIndexed { index, item ->
      val marker = when {
        item.task == TaskMark.Done -> "☑"
        item.task == TaskMark.Open -> "☐"
        list.ordered -> "${list.start + index}."
        else -> "•"
      }
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.s8)) {
        Text(marker, style = GlorianaTheme.type.reading, color = GlorianaTheme.colors.textMuted)
        FieldContent(item.blocks, panel, Modifier.weight(1f))
      }
    }
  }
}

internal fun codeBlockStyle(reading: TextStyle): TextStyle =
  reading.copy(fontFamily = FontFamily.Monospace, fontSize = reading.fontSize * CODE_BLOCK)

@Composable
private fun Preformatted(text: String) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.tag
  Box(
    Modifier
      .fillMaxWidth()
      .background(colors.surface2, shape)
      .border(1.dp, colors.borderSubtle, shape)
      .horizontalScroll(rememberScrollState())
      .padding(GlorianaTheme.spacing.s10),
  ) {
    Text(
      text,
      style = codeBlockStyle(GlorianaTheme.type.reading),
      color = colors.text,
      softWrap = false,
    )
  }
}

@Composable
private fun DetailsView(details: FieldBlock.Details, panel: Color) {
  var open by rememberSaveable(details) { mutableStateOf(false) }
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.control
  val spacing = GlorianaTheme.spacing
  Column(Modifier.fillMaxWidth().border(1.dp, colors.border, shape)) {
    Row(
      Modifier
        .fillMaxWidth()
        .tappable(shape) { open = !open }
        .padding(spacing.s10),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(spacing.s6),
    ) {
      val icon = if (open) Res.drawable.ic_expand_more else Res.drawable.ic_chevron_right
      Icon(painterResource(icon), null, tint = colors.textMuted)
      val label = GlorianaTheme.type.material.labelLarge
      if (details.summary.isEmpty()) {
        Text(stringResource(Res.string.details_summary), style = label, color = colors.textMuted)
      } else {
        FieldText(details.summary, label.copy(color = colors.textMuted), panel)
      }
    }
    if (open) {
      val inset = Modifier.padding(start = spacing.s10, end = spacing.s10, bottom = spacing.s10)
      FieldContent(details.blocks, panel, inset)
    }
  }
}

@Composable
private fun ImagePlaceholder(alt: String) {
  val colors = GlorianaTheme.colors
  val radius = GlorianaTheme.shapes.controlRadius
  val text = if (alt.isBlank()) {
    stringResource(Res.string.image_not_loaded)
  } else {
    stringResource(Res.string.image_not_loaded_alt, alt)
  }
  Row(
    Modifier
      .fillMaxWidth()
      .drawBehind {
        drawRoundRect(
          colors.borderStrong,
          cornerRadius = CornerRadius(radius.toPx()),
          style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))),
        )
      }
      .padding(GlorianaTheme.spacing.s12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8),
  ) {
    Icon(painterResource(Res.drawable.ic_image), null, tint = colors.textSubtle)
    Text(text, style = GlorianaTheme.type.material.bodyMedium, color = colors.textSubtle)
  }
}

package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.dismiss
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.resources.ic_error
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val InfoIconSize = 20.dp

@Composable
fun RetryNote(headline: String, detail: StringResource, modifier: Modifier = Modifier, onRetry: () -> Unit) {
  NoteCard(modifier) {
    NoteLine(boldThenPlain(headline, stringResource(detail)))
    GlorianaButton(stringResource(Res.string.retry), onRetry, icon = Res.drawable.ic_refresh_20)
  }
}

fun boldThenPlain(bold: String, plain: String?): AnnotatedString = boldThenPlain(AnnotatedString(bold), plain)

fun boldThenPlain(bold: AnnotatedString, plain: String?): AnnotatedString = buildAnnotatedString {
  withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(bold) }
  if (plain != null) append(" $plain")
}

@Composable
fun NoteLine(text: AnnotatedString, modifier: Modifier = Modifier) {
  Text(text, modifier, style = GlorianaTheme.type.material.bodyMedium, color = GlorianaTheme.colors.dangerText)
}

@Composable
fun NoteCard(
  modifier: Modifier = Modifier,
  onDismiss: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = GlorianaTheme.colors
  CardFrame(colors.dangerSoft, colors.dangerBorder, onDismiss, modifier) {
    Icon(painterResource(Res.drawable.ic_error), null, tint = colors.dangerText)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8), content = content)
  }
}

@Composable
fun InfoCard(
  icon: DrawableResource,
  modifier: Modifier = Modifier,
  onDismiss: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = GlorianaTheme.colors
  CardFrame(colors.surface2, colors.borderSubtle, onDismiss, modifier) {
    Icon(painterResource(icon), null, Modifier.size(InfoIconSize), tint = colors.textMuted)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8), content = content)
  }
}

@Composable
private fun CardFrame(
  fill: Color,
  border: Color,
  onDismiss: (() -> Unit)?,
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.control
  val endPadding = if (onDismiss == null) spacing.s12 else 0.dp
  Row(
    modifier
      .fillMaxWidth()
      .background(fill, shape)
      .border(1.dp, border, shape)
      .padding(start = spacing.s12, top = spacing.s12, bottom = spacing.s12, end = endPadding),
    horizontalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    content()
    if (onDismiss != null) {
      val dismiss = stringResource(Res.string.dismiss)
      GlorianaIconButton(Res.drawable.ic_close, dismiss, onDismiss, Modifier.align(Alignment.Top))
    }
  }
}

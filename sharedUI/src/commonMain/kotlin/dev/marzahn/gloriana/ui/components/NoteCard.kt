package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun RetryNote(headline: String, detail: StringResource, modifier: Modifier = Modifier, onRetry: () -> Unit) {
  NoteCard(modifier) {
    NoteLine(boldThenPlain(headline, stringResource(detail)))
    GlorianaButton(stringResource(Res.string.retry), onRetry, icon = Res.drawable.ic_refresh_20)
  }
}

fun boldThenPlain(bold: String, plain: String?): AnnotatedString = buildAnnotatedString {
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
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.control
  val endPadding = if (onDismiss == null) spacing.s12 else 0.dp
  Row(
    modifier
      .fillMaxWidth()
      .background(colors.dangerSoft, shape)
      .border(1.dp, colors.dangerBorder, shape)
      .padding(start = spacing.s12, top = spacing.s12, bottom = spacing.s12, end = endPadding),
    horizontalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    Icon(painterResource(Res.drawable.ic_error), null, tint = colors.dangerText)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.s8), content = content)
    if (onDismiss != null) {
      val dismiss = stringResource(Res.string.dismiss)
      GlorianaIconButton(Res.drawable.ic_close, dismiss, onDismiss, Modifier.align(Alignment.Top))
    }
  }
}

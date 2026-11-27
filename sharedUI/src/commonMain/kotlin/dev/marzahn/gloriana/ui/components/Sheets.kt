package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import dev.marzahn.gloriana.ui.theme.lookShadow
import org.jetbrains.compose.resources.stringResource

private val DialogMaxWidth = 480.dp

private enum class DialogKind { Plain, Alert }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetOrDialog(
  onDismiss: () -> Unit,
  kind: DialogKind,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.(SheetStyle) -> Unit,
) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val shapes = GlorianaTheme.shapes
  val scrim = Scrim(colors.scrim, colors.scrimAlpha)
  val style = GlorianaTheme.widthClass.sheetStyle
  val body: @Composable ColumnScope.() -> Unit = { content(style) }
  if (style == SheetStyle.BottomSheet) {
    ModalBottomSheet(
      onDismissRequest = onDismiss,
      shape = shapes.sheet,
      containerColor = colors.raised,
      contentColor = colors.text,
      tonalElevation = 0.dp,
      scrimColor = scrim.tinted,
    ) {
      Column(modifier.padding(horizontal = spacing.s24).padding(bottom = spacing.s24), content = body)
    }
    return
  }
  val panel = @Composable {
    ApplyDialogScrim(scrim)
    Column(
      modifier
        .padding(spacing.s24)
        .widthIn(max = DialogMaxWidth)
        .lookShadow(GlorianaTheme.shadows.elevated, shapes.panel)
        .background(colors.raised, shapes.panel)
        .padding(spacing.s24),
      content = body,
    )
  }
  when (kind) {
    DialogKind.Plain -> Dialog(onDismiss, dialogProperties(scrim), panel)
    DialogKind.Alert -> BasicAlertDialog(onDismiss, properties = dialogProperties(scrim), content = panel)
  }
}

@Composable
fun GlorianaSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  SheetOrDialog(onDismiss, DialogKind.Plain, modifier) { content() }
}

@Composable
fun GlorianaConfirmation(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cancelFocus = remember { FocusRequester() }
  val spacing = GlorianaTheme.spacing
  SheetOrDialog(onDismiss, DialogKind.Alert, modifier) { style ->
    Text(title, style = GlorianaTheme.type.material.headlineSmall, color = GlorianaTheme.colors.text)
    Text(
      message,
      Modifier.padding(top = spacing.s8, bottom = spacing.s24),
      style = GlorianaTheme.type.material.bodyMedium,
      color = GlorianaTheme.colors.textMuted,
    )
    val cancel = @Composable { buttonModifier: Modifier ->
      GlorianaButton(stringResource(Res.string.cancel), onDismiss, buttonModifier.focusRequester(cancelFocus))
    }
    val confirm = @Composable { buttonModifier: Modifier ->
      GlorianaButton(confirmLabel, onConfirm, buttonModifier, kind = ButtonKind.Danger)
    }
    when (style) {
      SheetStyle.BottomSheet -> Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
        confirm(Modifier.fillMaxWidth())
        cancel(Modifier.fillMaxWidth())
      }
      SheetStyle.Dialog -> FlowRow(
        Modifier.align(Alignment.End),
        horizontalArrangement = Arrangement.spacedBy(spacing.s8, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(spacing.s8),
      ) {
        cancel(Modifier)
        confirm(Modifier)
      }
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
  }
}

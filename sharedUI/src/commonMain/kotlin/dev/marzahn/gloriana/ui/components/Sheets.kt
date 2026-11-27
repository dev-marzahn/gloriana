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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
  dismissible: Boolean,
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
      sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
      sheetGesturesEnabled = dismissible,
      shape = shapes.sheet,
      containerColor = colors.raised,
      contentColor = colors.text,
      tonalElevation = 0.dp,
      scrimColor = scrim.tinted,
      dragHandle = if (dismissible) ({ BottomSheetDefaults.DragHandle() }) else null,
      properties = ModalBottomSheetProperties(
        shouldDismissOnBackPress = dismissible,
        shouldDismissOnClickOutside = dismissible,
      ),
    ) {
      val top = if (dismissible) 0.dp else spacing.s24
      Column(modifier.padding(horizontal = spacing.s24).padding(top = top, bottom = spacing.s24), content = body)
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
  val properties = dialogProperties(scrim, dismissible)
  when (kind) {
    DialogKind.Plain -> Dialog(onDismiss, properties, panel)
    DialogKind.Alert -> BasicAlertDialog(onDismiss, properties = properties, content = panel)
  }
}

@Composable
fun GlorianaSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  dismissible: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  SheetOrDialog(onDismiss, DialogKind.Plain, dismissible, modifier) { content() }
}

@Composable
fun SheetButtons(
  modifier: Modifier = Modifier,
  buttons: @Composable (Modifier) -> Unit,
) {
  val spacing = GlorianaTheme.spacing
  val stacked = GlorianaTheme.widthClass.sheetStyle == SheetStyle.BottomSheet
  FlowRow(
    modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(spacing.s8, Alignment.End),
    verticalArrangement = Arrangement.spacedBy(spacing.s8),
    maxItemsInEachRow = if (stacked) 1 else Int.MAX_VALUE,
  ) {
    buttons(if (stacked) Modifier.fillMaxWidth() else Modifier)
  }
}

class Confirmation(
  val title: String,
  val lines: List<String>,
  val confirmLabel: String,
  val cancelLabel: String? = null,
)

@Composable
fun GlorianaConfirmation(
  confirmation: Confirmation,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  busy: Boolean = false,
) {
  val cancelFocus = remember { FocusRequester() }
  val spacing = GlorianaTheme.spacing
  val type = GlorianaTheme.type.material
  val dismiss = { if (!busy) onDismiss() }
  SheetOrDialog(dismiss, DialogKind.Alert, dismissible = !busy, modifier) { style ->
    Text(confirmation.title, style = type.headlineSmall, color = GlorianaTheme.colors.text)
    confirmation.lines.forEach { line ->
      Text(line, Modifier.padding(top = spacing.s8), style = type.bodyMedium, color = GlorianaTheme.colors.textMuted)
    }
    val cancelLabel = confirmation.cancelLabel ?: stringResource(Res.string.cancel)
    val cancel = @Composable { buttonModifier: Modifier ->
      GlorianaButton(
        cancelLabel,
        dismiss,
        buttonModifier.focusRequester(cancelFocus),
        kind = ButtonKind.Ghost,
        enabled = !busy,
      )
    }
    val confirm = @Composable { buttonModifier: Modifier ->
      GlorianaButton(confirmation.confirmLabel, onConfirm, buttonModifier, kind = ButtonKind.Danger, enabled = !busy)
    }
    SheetButtons(Modifier.padding(top = spacing.s24)) { buttonModifier ->
      if (style == SheetStyle.BottomSheet) {
        confirm(buttonModifier)
        cancel(buttonModifier)
      } else {
        cancel(buttonModifier)
        confirm(buttonModifier)
      }
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
  }
}

package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaRadioButton
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.NoteCard
import dev.marzahn.gloriana.ui.components.NoteLine
import dev.marzahn.gloriana.ui.components.SearchField
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.components.Skeleton
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.components.SkeletonSurface
import dev.marzahn.gloriana.ui.components.boldThenPlain
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.asking_models
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.filter_models
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.model_list_failed
import dev.marzahn.gloriana.ui.resources.no_model_matches
import dev.marzahn.gloriana.ui.resources.pick_model_count
import dev.marzahn.gloriana.ui.resources.pick_model_title
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 6
private val ListHeight = 360.dp
private val SkeletonLine = 14.dp
private val SkeletonWidth = 200.dp
private val RowHeight = 48.dp

@Composable
fun ModelPickerSheet(
  host: String,
  list: ModelList,
  current: String,
  onPick: (String) -> Unit,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  var filter by rememberSaveable { mutableStateOf("") }
  GlorianaSheet(onDismiss, modifier) {
    Text(stringResource(Res.string.pick_model_title), style = type.headlineSmall, color = colors.text)
    val subtitle = when (list) {
      ModelList.Asking -> stringResource(Res.string.asking_models, host)
      is ModelList.Listed ->
        pluralStringResource(Res.plurals.pick_model_count, list.models.size, host, list.models.size)
      is ModelList.Failed -> null
    }
    subtitle?.let { Text(it, Modifier.padding(top = spacing.s8), style = type.bodyMedium, color = colors.textMuted) }
    Column(Modifier.padding(top = spacing.s16), verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
      when (list) {
        ModelList.Asking -> Skeleton { ModelSkeletons() }
        is ModelList.Listed -> {
          SearchField(filter, { filter = it }, stringResource(Res.string.filter_models), Modifier.fillMaxWidth())
          ModelRows(list.models, filter, current, onPick)
        }
        is ModelList.Failed -> NoteCard {
          when (val refusal = list.refusal) {
            is Refusal.Host -> NoteLine(
              boldThenPlain(
                stringResource(Res.string.model_list_failed, refusal.failure.reason()),
                refusal.failure.details(StepFor.ModelList),
              ),
            )
            is Refusal.Keys -> NoteLine(refusal.note.text())
          }
          GlorianaButton(stringResource(Res.string.retry), onRetry, icon = Res.drawable.ic_refresh_20)
        }
      }
    }
    if (GlorianaTheme.widthClass.sheetStyle == SheetStyle.Dialog) {
      SheetButtons(Modifier.padding(top = spacing.s16)) { buttonModifier ->
        GlorianaButton(stringResource(Res.string.cancel), onDismiss, buttonModifier, kind = ButtonKind.Ghost)
      }
    }
  }
}

@Composable
private fun ModelRows(models: List<String>, filter: String, current: String, onPick: (String) -> Unit) {
  val shown = models.filter { it.contains(filter.trim(), ignoreCase = true) }
  if (shown.isEmpty()) {
    Text(
      stringResource(Res.string.no_model_matches, filter.trim()),
      style = GlorianaTheme.type.material.bodyMedium,
      color = GlorianaTheme.colors.textMuted,
    )
    return
  }
  LazyColumn(Modifier.fillMaxWidth().heightIn(max = ListHeight)) {
    items(shown, key = { it }) { model ->
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = RowHeight)
          .tappable(GlorianaTheme.shapes.control, Role.RadioButton) { onPick(model) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8),
      ) {
        GlorianaRadioButton(model == current, onClick = null)
        Text(model, style = GlorianaTheme.type.material.bodyLarge, color = GlorianaTheme.colors.text)
      }
    }
  }
}

@Composable
private fun ModelSkeletons() {
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s14)) {
    repeat(SKELETON_ROWS) {
      SkeletonBlock(Modifier.width(SkeletonWidth).height(SkeletonLine), on = SkeletonSurface.Raised)
    }
  }
}

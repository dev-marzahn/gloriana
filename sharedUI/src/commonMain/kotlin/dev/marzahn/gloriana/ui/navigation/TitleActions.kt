package dev.marzahn.gloriana.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.components.ButtonSize
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaIconButton
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.done
import dev.marzahn.gloriana.ui.resources.ic_add_20
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_drag_indicator
import dev.marzahn.gloriana.ui.resources.ic_swap_vert
import dev.marzahn.gloriana.ui.resources.reorder_hint
import dev.marzahn.gloriana.ui.resources.reorder_hint_keys
import dev.marzahn.gloriana.ui.resources.reorder_kind
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val HintIconSize = 16.dp

class ReorderControl(
  val kind: String,
  val active: Boolean,
  val onStart: () -> Unit,
  val onDone: () -> Unit,
  val endsOnEscape: () -> Boolean,
)

@Composable
internal fun TitleActions(onCreate: (() -> Unit)?, reorder: ReorderControl?, size: ButtonSize) {
  Row(horizontalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    when {
      reorder?.active == true ->
        GlorianaButton(stringResource(Res.string.done), reorder.onDone, icon = Res.drawable.ic_check_20, size = size)
      else -> {
        reorder?.let { ReorderButton(it) }
        onCreate?.let {
          GlorianaButton(stringResource(Res.string.create), it, icon = Res.drawable.ic_add_20, size = size)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderButton(reorder: ReorderControl) {
  val label = stringResource(Res.string.reorder_kind, reorder.kind)
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
    tooltip = {
      PlainTooltip(containerColor = GlorianaTheme.colors.inverse, contentColor = GlorianaTheme.colors.inverseFg) {
        Text(label)
      }
    },
    state = rememberTooltipState(),
  ) {
    GlorianaIconButton(Res.drawable.ic_swap_vert, label, reorder.onStart)
  }
}

@Composable
internal fun ReorderHint(withKeys: Boolean, modifier: Modifier = Modifier) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Row(
    modifier.fillMaxWidth().padding(horizontal = spacing.s16, vertical = spacing.s8),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.s6),
  ) {
    Icon(painterResource(Res.drawable.ic_drag_indicator), null, Modifier.size(HintIconSize), tint = colors.textSubtle)
    Text(
      stringResource(if (withKeys) Res.string.reorder_hint_keys else Res.string.reorder_hint),
      style = GlorianaTheme.type.material.bodySmall,
      color = colors.textSubtle,
    )
  }
}

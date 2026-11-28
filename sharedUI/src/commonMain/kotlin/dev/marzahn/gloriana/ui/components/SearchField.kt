package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_close
import dev.marzahn.gloriana.ui.resources.ic_search
import dev.marzahn.gloriana.ui.resources.search_clear
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.animateLookColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val ListDensity.searchHeight: Dp
  get() = when (this) {
    ListDensity.Screen -> 44.dp
    ListDensity.Pane -> 40.dp
  }

@Composable
fun SearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  density: ListDensity = ListDensity.Screen,
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val shape = GlorianaTheme.shapes.control
  val spacing = GlorianaTheme.spacing
  val focusManager = LocalFocusManager.current
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  val outline by animateLookColor(if (focused) colors.accent else colors.borderStrong)
  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.onPreviewKeyEvent { event ->
      if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) return@onPreviewKeyEvent false
      if (query.isNotEmpty()) onQueryChange("") else focusManager.clearFocus()
      true
    },
    singleLine = true,
    textStyle = type.bodyLarge.copy(color = colors.text),
    cursorBrush = SolidColor(colors.accent),
    interactionSource = interactionSource,
    decorationBox = { field ->
      Row(
        Modifier
          .background(colors.raised, shape)
          .border(1.dp, outline, shape)
          .heightIn(min = density.searchHeight)
          .padding(horizontal = spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s8),
      ) {
        Icon(painterResource(Res.drawable.ic_search), null, tint = colors.textSubtle)
        Box(Modifier.weight(1f)) {
          if (query.isEmpty()) Text(placeholder, style = type.bodyLarge, color = colors.textSubtle, maxLines = 1)
          field()
        }
        if (query.isNotEmpty()) {
          GlorianaIconButton(Res.drawable.ic_close, stringResource(Res.string.search_clear), { onQueryChange("") })
        }
      }
    },
  )
}

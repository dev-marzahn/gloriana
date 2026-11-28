package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.ic_arrow_drop_down
import dev.marzahn.gloriana.ui.resources.ic_more_horiz
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.disabled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val MenuIconSize = 20.dp

class MenuItem(
  val label: String,
  val icon: DrawableResource,
  val onClick: () -> Unit,
  val enabled: Boolean = true,
  val detail: String? = null,
)

@Composable
fun GlorianaMoreMenu(contentDescription: String, items: List<MenuItem>, modifier: Modifier = Modifier) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier) {
    GlorianaIconButton(Res.drawable.ic_more_horiz, contentDescription, { open = true })
    GlorianaMenu(open, { open = false }, items)
  }
}

@Composable
private fun GlorianaMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
  val colors = GlorianaTheme.colors
  DropdownMenu(expanded, onDismiss, containerColor = colors.raised) {
    items.forEach { item ->
      DropdownMenuItem(
        text = {
          Column {
            Text(item.label, style = GlorianaTheme.type.material.bodyLarge)
            item.detail?.let { Text(it, style = GlorianaTheme.type.material.bodySmall, color = colors.textSubtle) }
          }
        },
        onClick = {
          onDismiss()
          item.onClick()
        },
        enabled = item.enabled,
        leadingIcon = { Icon(painterResource(item.icon), null, Modifier.size(MenuIconSize)) },
        colors = MenuDefaults.itemColors(
          textColor = colors.text,
          leadingIconColor = colors.textMuted,
          disabledTextColor = colors.text.disabled(),
          disabledLeadingIconColor = colors.textMuted.disabled(),
        ),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> GlorianaDropdownField(
  selected: T?,
  options: List<T>,
  label: @Composable (T) -> String,
  onSelect: (T) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  error: String? = null,
  enabled: Boolean = true,
  detail: (@Composable (T) -> String)? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val colors = GlorianaTheme.colors
  ExposedDropdownMenuBox(expanded, { expanded = it && enabled }, modifier) {
    GlorianaTextField(
      value = selected?.let { label(it) }.orEmpty(),
      onValueChange = {},
      modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
      enabled = enabled,
      readOnly = true,
      placeholder = placeholder,
      error = error,
      trailing = { Icon(painterResource(Res.drawable.ic_arrow_drop_down), null, tint = colors.textMuted) },
    )
    ExposedDropdownMenu(expanded, { expanded = false }, containerColor = colors.raised) {
      options.forEach { option ->
        DropdownMenuItem(
          text = {
            Column {
              Text(label(option), style = GlorianaTheme.type.material.bodyLarge)
              detail?.let { Text(it(option), style = GlorianaTheme.type.material.bodySmall, color = colors.textSubtle) }
            }
          },
          onClick = {
            expanded = false
            onSelect(option)
          },
          colors = MenuDefaults.itemColors(textColor = colors.text),
        )
      }
    }
  }
}

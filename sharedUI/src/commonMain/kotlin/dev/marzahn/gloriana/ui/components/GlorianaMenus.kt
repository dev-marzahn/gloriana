package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.layout.Box
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val MenuIconSize = 20.dp

class MenuItem(val label: String, val icon: DrawableResource, val onClick: () -> Unit)

@Composable
fun GlorianaMoreMenu(contentDescription: String, items: List<MenuItem>, modifier: Modifier = Modifier) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier) {
    GlorianaIconButton(Res.drawable.ic_more_horiz, contentDescription, { open = true })
    GlorianaMenu(open, { open = false }, items)
  }
}

@Composable
fun GlorianaMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
  val colors = GlorianaTheme.colors
  DropdownMenu(expanded, onDismiss, containerColor = colors.raised) {
    items.forEach { item ->
      DropdownMenuItem(
        text = { Text(item.label, style = GlorianaTheme.type.material.bodyLarge) },
        onClick = {
          onDismiss()
          item.onClick()
        },
        leadingIcon = { Icon(painterResource(item.icon), null, Modifier.size(MenuIconSize)) },
        colors = MenuDefaults.itemColors(textColor = colors.text, leadingIconColor = colors.textMuted),
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
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val colors = GlorianaTheme.colors
  ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
    GlorianaTextField(
      value = selected?.let { label(it) }.orEmpty(),
      onValueChange = {},
      modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
      readOnly = true,
      placeholder = placeholder,
      error = error,
      trailing = { Icon(painterResource(Res.drawable.ic_arrow_drop_down), null, tint = colors.textMuted) },
    )
    ExposedDropdownMenu(expanded, { expanded = false }, containerColor = colors.raised) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(label(option), style = GlorianaTheme.type.material.bodyLarge) },
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

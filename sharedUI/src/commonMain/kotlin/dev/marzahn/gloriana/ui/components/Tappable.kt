package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import dev.marzahn.gloriana.ui.theme.GlorianaIndication

fun Modifier.tappable(
  shape: Shape,
  role: Role = Role.Button,
  onClickLabel: String? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
): Modifier = clickable(
  interactionSource = null,
  indication = GlorianaIndication(shape),
  enabled = enabled,
  onClickLabel = onClickLabel,
  role = role,
  onClick = onClick,
)

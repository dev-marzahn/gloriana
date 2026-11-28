package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import dev.marzahn.gloriana.ui.theme.focusRing

internal fun Modifier.describingState(description: String?): Modifier =
  if (description == null) this else semantics { stateDescription = description }

@Composable
internal fun ControlFrame(
  interactionSource: MutableInteractionSource,
  shape: Shape,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
    Box(Modifier.focusRing(interactionSource, shape), propagateMinConstraints = true) {
      CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
    }
  }
}

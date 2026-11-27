package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.SettingRow
import org.jetbrains.compose.resources.stringResource

private val SamplerFieldWidth = 96.dp

@Composable
fun SamplerRow(
  sampler: Sampler,
  text: String,
  hint: String,
  error: SamplerError?,
  onChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  fieldModifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val errorText = error?.text()
  SettingRow(stringResource(sampler.label), hint, errorText, modifier) {
    GlorianaTextField(
      value = text,
      onValueChange = onChange,
      modifier = fieldModifier.width(SamplerFieldWidth),
      enabled = enabled,
      error = errorText,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
  }
}

package dev.marzahn.gloriana.ui.characters.fields

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberFieldBlocks(source: String): State<List<FieldBlock>?> = produceState<List<FieldBlock>?>(null, source) {
  value = withContext(Dispatchers.Default) { parseFieldMarkup(source) }
}

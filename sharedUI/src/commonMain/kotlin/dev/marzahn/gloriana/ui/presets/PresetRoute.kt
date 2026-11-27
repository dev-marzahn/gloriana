package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class PresetRoute(val id: String) : NavKey

@Composable
fun PresetScreen(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxSize())
}

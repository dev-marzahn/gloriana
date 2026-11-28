package dev.marzahn.gloriana.ui.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun joinedInPairs(parts: List<String>, pair: StringResource): String =
  parts.drop(1).fold(parts.firstOrNull().orEmpty()) { joined, part -> stringResource(pair, joined, part) }

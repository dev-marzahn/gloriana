package dev.marzahn.gloriana.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.components.SkeletonBlock
import dev.marzahn.gloriana.ui.theme.GlorianaTheme

private const val SKELETON_ROWS = 3
private val SkeletonTile = 40.dp
private val SkeletonLine = 12.dp
private val SkeletonName = 140.dp
private val SkeletonDetail = 180.dp

@Composable
fun RowSkeletons(modifier: Modifier = Modifier) {
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s12), modifier = modifier.padding(spacing.s8)) {
    repeat(SKELETON_ROWS) {
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.s12)) {
        SkeletonBlock(Modifier.size(SkeletonTile))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
          SkeletonBlock(Modifier.width(SkeletonName).height(SkeletonLine))
          SkeletonBlock(Modifier.width(SkeletonDetail).height(SkeletonLine))
          SkeletonBlock(Modifier.width(SkeletonName).height(SkeletonLine))
        }
      }
    }
  }
}

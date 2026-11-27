package dev.marzahn.gloriana.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

private val TopLevelSides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
private val SidesBesideRail = WindowInsetsSides.End + WindowInsetsSides.Vertical
private val RailSides = WindowInsetsSides.Start + WindowInsetsSides.Vertical

internal object ShellInsets {
  val safeDrawingWithoutKeyboard: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

  val topLevelScreen: WindowInsets
    @Composable get() = safeDrawingWithoutKeyboard.only(TopLevelSides)

  val besideRail: WindowInsets
    @Composable get() = safeDrawingWithoutKeyboard.only(SidesBesideRail)

  val rail: WindowInsets
    @Composable get() = safeDrawingWithoutKeyboard.only(RailSides)
}

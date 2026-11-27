package dev.marzahn.gloriana.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Navigation { Bar, LabelledRail, Workbench }

enum class SheetStyle { BottomSheet, Dialog }

enum class WidthClass(
  val navigation: Navigation,
  val sheetStyle: SheetStyle,
  val pointerDense: Boolean,
) {
  Compact(Navigation.Bar, SheetStyle.BottomSheet, pointerDense = false),
  Medium(Navigation.LabelledRail, SheetStyle.Dialog, pointerDense = false),
  Expanded(Navigation.Workbench, SheetStyle.Dialog, pointerDense = true),
  ;

  val minimumTouchTarget: Dp get() = if (pointerDense) Dp.Unspecified else TouchTarget

  companion object {
    private val MediumFrom = 600.dp
    private val ExpandedFrom = 840.dp
    private val TouchTarget = 48.dp

    fun of(width: Dp): WidthClass = when {
      width < MediumFrom -> Compact
      width < ExpandedFrom -> Medium
      else -> Expanded
    }
  }
}

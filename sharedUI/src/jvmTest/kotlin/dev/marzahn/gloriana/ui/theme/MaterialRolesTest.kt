package dev.marzahn.gloriana.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialRolesTest {
  private fun ColorScheme.roles(): Map<String, Color> = mapOf(
    "primary" to primary, "onPrimary" to onPrimary,
    "primaryContainer" to primaryContainer, "onPrimaryContainer" to onPrimaryContainer,
    "inversePrimary" to inversePrimary,
    "secondary" to secondary, "onSecondary" to onSecondary,
    "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
    "tertiary" to tertiary, "onTertiary" to onTertiary,
    "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
    "error" to error, "onError" to onError,
    "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
    "background" to background, "onBackground" to onBackground,
    "surface" to surface, "onSurface" to onSurface,
    "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
    "surfaceTint" to surfaceTint,
    "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
    "outline" to outline, "outlineVariant" to outlineVariant,
    "scrim" to scrim,
    "surfaceDim" to surfaceDim, "surfaceBright" to surfaceBright,
    "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
    "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
    "surfaceContainerHighest" to surfaceContainerHighest,
    "primaryFixed" to primaryFixed, "primaryFixedDim" to primaryFixedDim,
    "onPrimaryFixed" to onPrimaryFixed, "onPrimaryFixedVariant" to onPrimaryFixedVariant,
    "secondaryFixed" to secondaryFixed, "secondaryFixedDim" to secondaryFixedDim,
    "onSecondaryFixed" to onSecondaryFixed, "onSecondaryFixedVariant" to onSecondaryFixedVariant,
    "tertiaryFixed" to tertiaryFixed, "tertiaryFixedDim" to tertiaryFixedDim,
    "onTertiaryFixed" to onTertiaryFixed, "onTertiaryFixedVariant" to onTertiaryFixedVariant,
  )

  private val fixed = mapOf(
    "primaryFixed" to 0xFFF5D9E1, "primaryFixedDim" to 0xFFE596AD,
    "onPrimaryFixed" to 0xFF3B1823, "onPrimaryFixedVariant" to 0xFF7A3449,
    "secondaryFixed" to 0xFFEAE3D9, "secondaryFixedDim" to 0xFFD6CEC2,
    "onSecondaryFixed" to 0xFF1C1917, "onSecondaryFixedVariant" to 0xFF4F4A45,
    "tertiaryFixed" to 0xFFEAE3D9, "tertiaryFixedDim" to 0xFFD6CEC2,
    "onTertiaryFixed" to 0xFF1C1917, "onTertiaryFixedVariant" to 0xFF4F4A45,
  )

  private val light = mapOf(
    "primary" to 0xFFA84D68, "onPrimary" to 0xFFFFFFFF,
    "primaryContainer" to 0xFFF3E7E6, "onPrimaryContainer" to 0xFF93405A,
    "inversePrimary" to 0xFFE596AD,
    "secondary" to 0xFF6B635E, "onSecondary" to 0xFFFFFFFF,
    "secondaryContainer" to 0xFFEAE3D9, "onSecondaryContainer" to 0xFF1C1917,
    "tertiary" to 0xFF4F4A45, "onTertiary" to 0xFFFFFFFF,
    "tertiaryContainer" to 0xFFF4EFE8, "onTertiaryContainer" to 0xFF1C1917,
    "error" to 0xFFDC2626, "onError" to 0xFFFFFFFF,
    "errorContainer" to 0xFFFCEEEC, "onErrorContainer" to 0xFFB91C1C,
    "background" to 0xFFFBF8F4, "onBackground" to 0xFF1C1917,
    "surface" to 0xFFFBF8F4, "onSurface" to 0xFF1C1917,
    "surfaceVariant" to 0xFFF4EFE8, "onSurfaceVariant" to 0xFF4F4A45,
    "surfaceTint" to 0xFFFBF8F4,
    "inverseSurface" to 0xFF1C1917, "inverseOnSurface" to 0xFFFFFFFF,
    "outline" to 0xFFD6CEC2, "outlineVariant" to 0xFFE7E0D6,
    "scrim" to 0xFF1C1917,
    "surfaceDim" to 0xFFEAE3D9, "surfaceBright" to 0xFFFFFFFF,
    "surfaceContainerLowest" to 0xFFFFFFFF, "surfaceContainerLow" to 0xFFFDFBF8,
    "surfaceContainer" to 0xFFF5F0E9, "surfaceContainerHigh" to 0xFFF4EFE8,
    "surfaceContainerHighest" to 0xFFEAE3D9,
  ) + fixed

  private val dark = mapOf(
    "primary" to 0xFFE596AD, "onPrimary" to 0xFF2A0D16,
    "primaryContainer" to 0xFF302426, "onPrimaryContainer" to 0xFFEBA8BC,
    "inversePrimary" to 0xFFA84D68,
    "secondary" to 0xFF9C948E, "onSecondary" to 0xFF121110,
    "secondaryContainer" to 0xFF2D2925, "onSecondaryContainer" to 0xFFE7E5E4,
    "tertiary" to 0xFFB8B2AD, "onTertiary" to 0xFF121110,
    "tertiaryContainer" to 0xFF23201D, "onTertiaryContainer" to 0xFFE7E5E4,
    "error" to 0xFFEF4444, "onError" to 0xFF1C1917,
    "errorContainer" to 0xFF2A1514, "onErrorContainer" to 0xFFF87171,
    "background" to 0xFF121110, "onBackground" to 0xFFE7E5E4,
    "surface" to 0xFF121110, "onSurface" to 0xFFE7E5E4,
    "surfaceVariant" to 0xFF23201D, "onSurfaceVariant" to 0xFFB8B2AD,
    "surfaceTint" to 0xFF121110,
    "inverseSurface" to 0xFFF5F5F4, "inverseOnSurface" to 0xFF1C1917,
    "outline" to 0xFF353230, "outlineVariant" to 0xFF252321,
    "scrim" to 0xFF000000,
    "surfaceDim" to 0xFF0E0D0C, "surfaceBright" to 0xFF2D2925,
    "surfaceContainerLowest" to 0xFF0E0D0C, "surfaceContainerLow" to 0xFF161412,
    "surfaceContainer" to 0xFF1A1816, "surfaceContainerHigh" to 0xFF23201D,
    "surfaceContainerHighest" to 0xFF2D2925,
  ) + fixed

  @Test
  fun lightSchemeSetsEveryRoleAsTheLookSays() {
    assertEquals(light.mapValues { Color(it.value) }, LightLookColors.toColorScheme().roles())
  }

  @Test
  fun darkSchemeSetsEveryRoleAsTheLookSays() {
    assertEquals(dark.mapValues { Color(it.value) }, DarkLookColors.toColorScheme().roles())
  }
}

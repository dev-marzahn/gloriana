package dev.marzahn.gloriana.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

const val DISABLED_ALPHA = 0.38f

fun Color.disabled(): Color = copy(alpha = alpha * DISABLED_ALPHA)

@Immutable
data class ContainerRoles(
  val lowest: Color,
  val low: Color,
  val middle: Color,
  val high: Color,
  val highest: Color,
  val dim: Color,
  val bright: Color,
)

@Immutable
data class FixedRoles(
  val primary: Color,
  val primaryDim: Color,
  val onPrimary: Color,
  val onPrimaryVariant: Color,
  val neutral: Color,
  val neutralDim: Color,
  val onNeutral: Color,
  val onNeutralVariant: Color,
)

@Immutable
data class LookColors(
  val accent: Color,
  val accentFg: Color,
  val accentHover: Color,
  val accentSoft: Color,
  val accentText: Color,
  val accentRing: Color,
  val accentChip: Color,
  val inverseAccent: Color,
  val canvas: Color,
  val rail: Color,
  val pane: Color,
  val raised: Color,
  val surface2: Color,
  val surface3: Color,
  val onNeutral: Color,
  val text: Color,
  val textMuted: Color,
  val textSubtle: Color,
  val borderStrong: Color,
  val border: Color,
  val borderSubtle: Color,
  val danger: Color,
  val onDanger: Color,
  val dangerSoft: Color,
  val dangerText: Color,
  val dangerBorder: Color,
  val inverse: Color,
  val inverseFg: Color,
  val onAvatar: Color,
  val scrim: Color,
  val scrimAlpha: Float,
  val pictureShade: Color,
  val containers: ContainerRoles,
  val fixed: FixedRoles,
)

private val Rose = Color(0xFFA84D68)
private val RoseLight = Color(0xFFE596AD)
private val Ink = Color(0xFF1C1917)
private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

private val Fixed = FixedRoles(
  primary = Color(0xFFF5D9E1),
  primaryDim = RoseLight,
  onPrimary = Color(0xFF3B1823),
  onPrimaryVariant = Color(0xFF7A3449),
  neutral = Color(0xFFEAE3D9),
  neutralDim = Color(0xFFD6CEC2),
  onNeutral = Ink,
  onNeutralVariant = Color(0xFF4F4A45),
)

private val LightRail = Color(0xFFF5F0E9)
private val LightPane = Color(0xFFFDFBF8)
private val LightSurface2 = Color(0xFFF4EFE8)
private val LightSurface3 = Color(0xFFEAE3D9)

val LightLookColors = LookColors(
  accent = Rose,
  accentFg = White,
  accentHover = Color(0xFF93405A),
  accentSoft = Color(0xFFF3E7E6),
  accentText = Color(0xFF93405A),
  accentRing = Rose.copy(alpha = 0.35f),
  accentChip = White.copy(alpha = 0.18f),
  inverseAccent = RoseLight,
  canvas = Color(0xFFFBF8F4),
  rail = LightRail,
  pane = LightPane,
  raised = White,
  surface2 = LightSurface2,
  surface3 = LightSurface3,
  onNeutral = White,
  text = Ink,
  textMuted = Color(0xFF4F4A45),
  textSubtle = Color(0xFF6B635E),
  borderStrong = Color(0xFFD6CEC2),
  border = Color(0xFFE7E0D6),
  borderSubtle = Color(0xFFEFE9E1),
  danger = Color(0xFFDC2626),
  onDanger = White,
  dangerSoft = Color(0xFFFCEEEC),
  dangerText = Color(0xFFB91C1C),
  dangerBorder = Color(0xFFFECACA),
  inverse = Ink,
  inverseFg = White,
  onAvatar = White,
  scrim = Ink,
  scrimAlpha = 0.4f,
  pictureShade = Black,
  containers = ContainerRoles(
    lowest = White,
    low = LightPane,
    middle = LightRail,
    high = LightSurface2,
    highest = LightSurface3,
    dim = LightSurface3,
    bright = White,
  ),
  fixed = Fixed,
)

private val DarkRail = Color(0xFF0E0D0C)
private val DarkPane = Color(0xFF161412)
private val DarkRaised = Color(0xFF1A1816)
private val DarkSurface2 = Color(0xFF23201D)
private val DarkSurface3 = Color(0xFF2D2925)
private val DarkCanvas = Color(0xFF121110)

val DarkLookColors = LookColors(
  accent = RoseLight,
  accentFg = Color(0xFF2A0D16),
  accentHover = Color(0xFFEBABBE),
  accentSoft = Color(0xFF302426),
  accentText = Color(0xFFEBA8BC),
  accentRing = RoseLight.copy(alpha = 0.4f),
  accentChip = Color(0xFF2A0D16).copy(alpha = 0.14f),
  inverseAccent = Rose,
  canvas = DarkCanvas,
  rail = DarkRail,
  pane = DarkPane,
  raised = DarkRaised,
  surface2 = DarkSurface2,
  surface3 = DarkSurface3,
  onNeutral = DarkCanvas,
  text = Color(0xFFE7E5E4),
  textMuted = Color(0xFFB8B2AD),
  textSubtle = Color(0xFF9C948E),
  borderStrong = Color(0xFF353230),
  border = Color(0xFF252321),
  borderSubtle = Color(0xFF1E1C1B),
  danger = Color(0xFFEF4444),
  onDanger = Ink,
  dangerSoft = Color(0xFF2A1514),
  dangerText = Color(0xFFF87171),
  dangerBorder = Color(0xFF4B1E1C),
  inverse = Color(0xFFF5F5F4),
  inverseFg = Ink,
  onAvatar = White,
  scrim = Black,
  scrimAlpha = 0.6f,
  pictureShade = Black,
  containers = ContainerRoles(
    lowest = DarkRail,
    low = DarkPane,
    middle = DarkRaised,
    high = DarkSurface2,
    highest = DarkSurface3,
    dim = DarkRail,
    bright = DarkSurface3,
  ),
  fixed = Fixed,
)

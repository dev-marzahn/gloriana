package dev.marzahn.gloriana.logic.brand

import kotlin.uuid.Uuid

data class AvatarGradientColors(val startRgb: Int, val endRgb: Int)

val AvatarGradientTable = listOf(
  AvatarGradientColors(startRgb = 0x3D4A6B, endRgb = 0x55618A),
  AvatarGradientColors(startRgb = 0x2F4858, endRgb = 0x3F6E70),
  AvatarGradientColors(startRgb = 0x5A4A78, endRgb = 0x7A5C96),
  AvatarGradientColors(startRgb = 0x6E5200, endRgb = 0x8A6414),
  AvatarGradientColors(startRgb = 0x3C5A3A, endRgb = 0x4E7044),
  AvatarGradientColors(startRgb = 0x4A3B33, endRgb = 0x6E5446),
)

fun avatarGradientColors(id: Uuid): AvatarGradientColors =
  AvatarGradientTable[id.hashCode().mod(AvatarGradientTable.size)]

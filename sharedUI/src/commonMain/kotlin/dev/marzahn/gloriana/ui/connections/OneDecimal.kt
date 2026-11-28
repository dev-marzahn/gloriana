package dev.marzahn.gloriana.ui.connections

import kotlin.math.abs
import kotlin.math.roundToLong

private const val TENTHS = 10

fun withOneDecimal(value: Double): String {
  val tenths = (value * TENTHS).roundToLong()
  val sign = if (tenths < 0) "-" else ""
  return "$sign${abs(tenths) / TENTHS}.${abs(tenths) % TENTHS}"
}

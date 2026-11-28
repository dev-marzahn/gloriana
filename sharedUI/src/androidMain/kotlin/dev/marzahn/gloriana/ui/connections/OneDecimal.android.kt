package dev.marzahn.gloriana.ui.connections

import java.util.Locale

actual fun withOneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

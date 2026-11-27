package dev.marzahn.gloriana.ui.presets

import kotlinx.coroutines.flow.MutableStateFlow

internal inline fun <S, T> MutableStateFlow<S>.updateAndClaim(change: (S) -> Pair<S, T>): T {
  while (true) {
    val before = value
    val (after, claimed) = change(before)
    if (compareAndSet(before, after)) return claimed
  }
}

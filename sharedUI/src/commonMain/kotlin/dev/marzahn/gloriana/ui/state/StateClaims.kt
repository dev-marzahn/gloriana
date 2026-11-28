package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.flow.MutableStateFlow

internal inline fun <S, T> MutableStateFlow<S>.updateAndClaim(change: (S) -> Pair<S, T>): T {
  while (true) {
    val before = value
    val (after, claimed) = change(before)
    if (compareAndSet(before, after)) return claimed
  }
}

interface StateCell<S> {
  fun <R : Any> claim(change: (S) -> Pair<S, R>?): R?
}

fun <S> MutableStateFlow<S>.asCell(): StateCell<S> = object : StateCell<S> {
  override fun <R : Any> claim(change: (S) -> Pair<S, R>?): R? =
    updateAndClaim { current -> change(current) ?: (current to null) }
}

fun <S> StateCell<S>.update(change: (S) -> S) {
  claim { current -> change(current) to Unit }
}

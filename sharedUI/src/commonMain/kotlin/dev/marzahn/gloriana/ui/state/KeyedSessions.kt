package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class KeyedSessions<K, S>(
  private val initial: (K) -> S,
  private val holdsNothing: (K, S) -> Boolean,
) {
  private val sessions = MutableStateFlow<Map<K, S>>(emptyMap())

  fun session(key: K): Flow<S> = sessions.map { it[key] ?: initial(key) }.distinctUntilChanged()

  fun current(key: K): S = sessions.value[key] ?: initial(key)

  fun update(key: K, change: (S) -> S) = cell(key).update(change)

  fun cell(key: K): StateCell<S> = object : StateCell<S> {
    override fun <R : Any> claim(change: (S) -> Pair<S, R>?): R? = sessions.updateAndClaim { all ->
      val result = change(all[key] ?: initial(key))
      if (result == null) all to null else all.with(key, result.first) to result.second
    }
  }

  fun whileHeld(key: K, change: (S) -> S) = sessions.update { all ->
    all[key]?.let { all.with(key, change(it)) } ?: all
  }

  fun forget(key: K) = sessions.update { it - key }

  private fun Map<K, S>.with(key: K, session: S) =
    if (holdsNothing(key, session)) this - key else this + (key to session)
}

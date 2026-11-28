package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

sealed interface StoredRow<out T : Any> {
  data object Gone : StoredRow<Nothing>
}

sealed interface StoredValue<out T : Any> : StoredRow<T> {
  data object Loading : StoredValue<Nothing>

  data object CouldNotLoad : StoredValue<Nothing>

  data class Found<T : Any>(val value: T) : StoredValue<T>
}

class StoredReads<T, R : Any> private constructor(
  private val loading: R,
  private val failed: R,
  private val found: (T) -> R,
) {
  private class Seen<R>(val key: Any, val attempt: Int, val row: R)

  private val attempts = MutableStateFlow(0)
  private val seen = MutableStateFlow<Seen<R>?>(null)

  fun retry() = attempts.update { it + 1 }

  fun latest(key: Any): R? = seen.value?.takeIf { it.key == key }?.row

  @OptIn(ExperimentalCoroutinesApi::class)
  fun follow(key: Any, read: () -> Flow<T>): Flow<R> = attempts.flatMapLatest { attempt ->
    read()
      .map(found)
      .onStart { emit(seen.value?.takeIf { it.key == key && it.attempt == attempt }?.row ?: loading) }
      .catch { emit(failed) }
      .onEach { row -> seen.value = Seen(key, attempt, row) }
  }

  companion object {
    fun <T : Any> values(): StoredReads<T, StoredValue<T>> =
      StoredReads(StoredValue.Loading, StoredValue.CouldNotLoad) { StoredValue.Found(it) }

    fun <T : Any> rows(): StoredReads<T?, StoredRow<T>> =
      StoredReads(StoredValue.Loading, StoredValue.CouldNotLoad) { value ->
        value?.let { StoredValue.Found(it) } ?: StoredRow.Gone
      }
  }
}

package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.uuid.Uuid

data class DefaultMarked<T>(val row: T, val isDefault: Boolean)

fun <T : Any> Flow<T?>.markedDefaultWhen(id: Uuid, default: Flow<Uuid?>): Flow<DefaultMarked<T>?> =
  combine(default) { row, defaultId -> row?.let { DefaultMarked(it, defaultId == id) } }

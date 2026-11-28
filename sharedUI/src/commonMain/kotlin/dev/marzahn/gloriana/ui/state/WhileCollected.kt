package dev.marzahn.gloriana.ui.state

import kotlinx.coroutines.flow.SharingStarted

private const val STOP_TIMEOUT_MILLIS = 5_000L

val WhileCollected: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)

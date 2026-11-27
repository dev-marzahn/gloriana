package dev.marzahn.gloriana.logic.storage

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.app.stopApp
import kotlinx.coroutines.cancel
import org.koin.dsl.module
import org.koin.dsl.onClose

val storageModule = module {
  single<IdFactory> { UuidV7IdFactory }
  single { UpdatedAtClock() }
  single { ApplicationScope { stopApp(it) } } onClose { it?.cancel() }
  single(createdAtStart = true) { DatabaseOpenedAtStart(get(), get()) }
  single { Tombstones(get(), get()) }
  single { ChatsUsing.NoneYet }
}

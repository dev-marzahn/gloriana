package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.Faults
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val vaultModule: Module = module {
  single { TinkKeyVault(get(), get(), get(), get()) } bind KeyVault::class
  single(createdAtStart = true) { DataKeysSweptAtStart(get(), get()) }
}

fun keyVault(library: LibraryDirectory, device: DeviceKeyStore, faults: Faults, stored: StoredConnections): KeyVault =
  TinkKeyVault(library, device, faults, stored)

internal class DataKeysSweptAtStart(vault: TinkKeyVault, scope: ApplicationScope) {
  init {
    scope.launch { vault.sweepAtStart() }
  }
}

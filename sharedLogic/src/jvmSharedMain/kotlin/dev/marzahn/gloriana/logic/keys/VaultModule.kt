package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.Faults
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val vaultModule: Module = module {
  single<KeyVault> { keyVault(get(), get(), get()) }
}

fun keyVault(library: LibraryDirectory, device: DeviceKeyStore, faults: Faults): KeyVault =
  TinkKeyVault(library, device, faults)

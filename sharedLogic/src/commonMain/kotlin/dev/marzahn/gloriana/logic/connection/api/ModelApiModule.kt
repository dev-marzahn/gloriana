package dev.marzahn.gloriana.logic.connection.api

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal expect val modelHttpModule: Module

val modelApiModule = module {
  includes(modelHttpModule)
  single { HostCalls(get()) }
  single { ModelReplies(get()) }
  single { VaultModelApi(get(), get(), get()) } bind ModelApi::class
  single { SecretsCheck(get()) }
  single { VaultConnectionProbe(get(), get(), get(), get()) } bind ConnectionProbe::class
}

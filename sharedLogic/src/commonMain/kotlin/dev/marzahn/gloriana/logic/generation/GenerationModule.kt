package dev.marzahn.gloriana.logic.generation

import org.koin.dsl.module

val generationModule = module {
  single { ReplyPreparation(get(), get(), get(), get(), get(), get()) }
  single { ReplyStream(get(), get(), get()) }
  single { LiveReplies() }
  single { ReplyRun(get(), get(), get(), get(), get()) }
  single { ReplyWriter(get(), get(), get(), get(), get(), get(), get()) }
}

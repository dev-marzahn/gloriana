package dev.marzahn.gloriana.logic.tasks

import org.koin.dsl.module

val tasksModule = module {
  single { TaskRunner(get(), get()) }
}

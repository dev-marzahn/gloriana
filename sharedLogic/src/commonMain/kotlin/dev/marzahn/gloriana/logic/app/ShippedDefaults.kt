package dev.marzahn.gloriana.logic.app

import dev.marzahn.gloriana.logic.personas.storage.DefaultPersona
import dev.marzahn.gloriana.logic.presets.storage.DefaultPreset
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.koin.dsl.module

enum class ShippedDefault {
  Persona,
  Preset,
}

class ShippedDefaults internal constructor(
  private val persona: DefaultPersona,
  private val preset: DefaultPreset,
  private val log: ProblemLog,
) {
  suspend fun ensure(): Set<ShippedDefault> = setOfNotNull(
    ShippedDefault.Persona.takeUnless { written(it, persona::ensureShipped) },
    ShippedDefault.Preset.takeUnless { written(it, preset::ensureShipped) },
  )

  private suspend fun written(default: ShippedDefault, ensure: suspend () -> Unit): Boolean = failingAs({ cause ->
    log.record("The shipped default $default could not be written; the next start tries again", cause)
    false
  }) {
    ensure()
    true
  }
}

class ShippedDefaultsAtStart(defaults: ShippedDefaults, scope: ApplicationScope) {
  val missing: Deferred<Set<ShippedDefault>> = scope.async { defaults.ensure() }
}

val shippedDefaultsModule = module {
  single { platformProblemLog }
  single { ShippedDefaults(get(), get(), get()) }
  single(createdAtStart = true) { ShippedDefaultsAtStart(get(), get()) }
}

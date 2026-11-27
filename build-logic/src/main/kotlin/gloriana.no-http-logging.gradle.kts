val shippedRuntime = if (plugins.hasPlugin("com.android.application")) "releaseRuntimeClasspath" else "runtimeClasspath"

val noHttpLogging = tasks.register<NoHttpLoggingTask>("checkNoHttpLogging") {
  runtimeModules.set(
    configurations.named(shippedRuntime).flatMap { it.incoming.resolutionResult.rootComponent }.map { it.moduleIds() },
  )
}

tasks.matching { it.name == "check" }.configureEach { dependsOn(noHttpLogging) }

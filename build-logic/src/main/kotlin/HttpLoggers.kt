import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

val HTTP_LOGGER_MODULES = setOf(
  "io.ktor:ktor-client-logging",
  "com.squareup.okhttp3:logging-interceptor",
)

fun httpLoggersIn(modules: Collection<String>): List<String> = modules.filter { it in HTTP_LOGGER_MODULES }.sorted()

fun ResolvedComponentResult.moduleIds(): Set<String> {
  val seen = mutableSetOf<ResolvedComponentResult>()
  val pending = ArrayDeque(listOf(this))
  while (pending.isNotEmpty()) {
    val component = pending.removeFirst()
    if (!seen.add(component)) continue
    component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { pending += it.selected }
  }
  return seen.mapNotNull { (it.id as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}" } }.toSet()
}

abstract class NoHttpLoggingTask : DefaultTask() {
  @get:Input
  abstract val runtimeModules: SetProperty<String>

  @TaskAction
  fun check() {
    val found = httpLoggersIn(runtimeModules.get())
    if (found.isNotEmpty()) throw GradleException("HTTP logging on the shipped runtime classpath: $found")
  }
}

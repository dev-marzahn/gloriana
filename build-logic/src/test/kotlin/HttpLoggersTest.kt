import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpLoggersTest {
  @Test
  fun findsEachHttpLoggerAmongTheModules() {
    val modules = listOf(
      "io.ktor:ktor-client-core",
      "com.squareup.okhttp3:okhttp",
      "io.ktor:ktor-client-logging",
      "com.squareup.okhttp3:logging-interceptor",
    )

    assertEquals(
      listOf("com.squareup.okhttp3:logging-interceptor", "io.ktor:ktor-client-logging"),
      httpLoggersIn(modules),
    )
  }

  @Test
  fun theHttpClientItselfPasses() {
    assertEquals(emptyList(), httpLoggersIn(listOf("io.ktor:ktor-client-okhttp", "com.squareup.okhttp3:okhttp")))
  }

  @Test
  fun theWalkReachesTransitiveModulesOnceAndSkipsProjects() {
    val logging = component(module("io.ktor", "ktor-client-logging"))
    val core = component(module("io.ktor", "ktor-client-core"), logging)
    val app = component(project(), core, logging)

    assertEquals(setOf("io.ktor:ktor-client-core", "io.ktor:ktor-client-logging"), app.moduleIds())
  }

  private fun module(group: String, name: String): ComponentIdentifier =
    fake(ModuleComponentIdentifier::class.java, "getGroup" to group, "getModule" to name)

  private fun project(): ComponentIdentifier = fake(ProjectComponentIdentifier::class.java)

  private fun component(id: ComponentIdentifier, vararg dependencies: ResolvedComponentResult): ResolvedComponentResult {
    val edges = dependencies.map { fake(ResolvedDependencyResult::class.java, "getSelected" to it) }.toSet()
    return fake(ResolvedComponentResult::class.java, "getId" to id, "getDependencies" to edges)
  }

  private fun <T> fake(type: Class<T>, vararg answers: Pair<String, Any>): T {
    val byName = answers.toMap()
    val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, arguments ->
      when (method.name) {
        "hashCode" -> System.identityHashCode(self)
        "equals" -> self === arguments?.firstOrNull()
        "toString" -> type.simpleName
        else -> byName[method.name]
      }
    }
    return type.cast(proxy)
  }
}

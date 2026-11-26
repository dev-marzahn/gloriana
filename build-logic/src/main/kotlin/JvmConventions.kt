import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

internal fun KotlinCommonCompilerOptions.failOnWarnings() {
  allWarningsAsErrors.set(true)
}

internal fun KotlinJvmCompilerOptions.targetJvmLevel() {
  jvmTarget.set(JvmLevel.kotlin)
}

internal fun KotlinJvmCompilerOptions.compileAgainstJvmLevel() {
  targetJvmLevel()
  freeCompilerArgs.add("-Xjdk-release=${JvmLevel.release}")
}

internal fun Project.compileJavaAgainstJvmLevel() {
  tasks.withType<JavaCompile>().configureEach {
    options.release.set(JvmLevel.release)
  }
}

internal fun Project.runTestsOnJUnitPlatform(
  implementation: NamedDomainObjectProvider<Configuration>,
  runtimeOnly: NamedDomainObjectProvider<Configuration>,
) {
  dependencies.add(implementation.name, catalog.library("kotlin-test-junit5"))
  dependencies.add(implementation.name, dependencies.platform(catalog.library("junit-bom")))
  dependencies.add(runtimeOnly.name, catalog.library("junit-platform-launcher"))
  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}

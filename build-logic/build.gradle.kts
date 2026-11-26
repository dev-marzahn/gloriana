plugins {
  `kotlin-dsl`
}

fun pluginArtifact(plugin: Provider<PluginDependency>) =
  plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

dependencies {
  implementation(pluginArtifact(libs.plugins.kotlin.jvm))
  implementation(pluginArtifact(libs.plugins.kotlin.multiplatform))
  implementation(pluginArtifact(libs.plugins.kotlin.compose))
  implementation(pluginArtifact(libs.plugins.kotlin.serialization))
  implementation(pluginArtifact(libs.plugins.android.application))
  implementation(pluginArtifact(libs.plugins.android.kotlin.multiplatform.library))
  implementation(pluginArtifact(libs.plugins.compose.multiplatform))
  implementation(pluginArtifact(libs.plugins.koin.compiler))
  implementation(pluginArtifact(libs.plugins.ksp))
  implementation(pluginArtifact(libs.plugins.room))
  implementation(pluginArtifact(libs.plugins.detekt))

  testImplementation(libs.kotlin.test)
  testImplementation(platform(libs.junit.bom))
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()
}

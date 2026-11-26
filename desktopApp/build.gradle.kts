import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  id("gloriana.kotlin-jvm")
  id("gloriana.compose")
  id("gloriana.detekt")
}

dependencies {
  implementation(projects.sharedUI)
  implementation(compose.desktop.currentOs)
  implementation(libs.compose.components.resources)
  implementation(libs.kotlinx.coroutines.swing)
  testImplementation(libs.koin.test)
}

compose.desktop {
  application {
    mainClass = "dev.marzahn.gloriana.desktop.MainKt"
    buildTypes.release.proguard.configurationFiles.from(layout.projectDirectory.file("proguard-rules.pro"))
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "Gloriana"
      packageVersion = appVersion.toString()
    }
  }
}

compose.resources {
  generateResClass = never
}

tasks.withType<JavaExec>().named { it == "run" }.configureEach {
  systemProperty("gloriana.debuggable", "true")
}

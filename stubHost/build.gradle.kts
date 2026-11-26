plugins {
  id("gloriana.kotlin-jvm")
  id("gloriana.detekt")
  id("gloriana.serialization")
  application
}

application {
  mainClass = "dev.marzahn.gloriana.stubhost.MainKt"
}

dependencies {
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.ktor.server.test.host)
}
